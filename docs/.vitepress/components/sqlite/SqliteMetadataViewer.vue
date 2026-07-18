<script setup>
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useData } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

const { lang } = useData()
const isVi = computed(() => lang.value.startsWith('vi'))

const copy = computed(() => isVi.value ? {
  dropTitle: 'Chọn cơ sở dữ liệu SQLite',
  dropHint: 'Kéo thả file vào đây hoặc chọn enderchests.db',
  choose: 'Chọn file SQLite',
  privacy: 'File chỉ được đọc trong trình duyệt và không được tải lên máy chủ.',
  loading: 'Đang mở cơ sở dữ liệu…',
  replace: 'Đổi file',
  close: 'Đóng',
  tables: 'Bảng',
  rows: 'hàng',
  search: 'Tìm trong bảng…',
  noRows: 'Không tìm thấy dữ liệu phù hợp.',
  previous: 'Trước',
  next: 'Sau',
  page: 'Trang',
  of: '/',
  blob: 'BLOB',
  emptyDb: 'Cơ sở dữ liệu không có bảng nào để hiển thị.',
  invalidDb: 'Không thể mở file này. Hãy chọn một cơ sở dữ liệu SQLite hợp lệ.',
  readError: 'Không thể đọc dữ liệu từ bảng đã chọn.',
  fileTooLarge: 'File quá lớn để mở an toàn trong trình duyệt (tối đa 100 MB).',
} : {
  dropTitle: 'Choose a SQLite database',
  dropHint: 'Drop a file here or select enderchests.db',
  choose: 'Choose SQLite file',
  privacy: 'The file is read only in your browser and is never uploaded.',
  loading: 'Opening database…',
  replace: 'Change file',
  close: 'Close',
  tables: 'Tables',
  rows: 'rows',
  search: 'Search this table…',
  noRows: 'No matching data found.',
  previous: 'Previous',
  next: 'Next',
  page: 'Page',
  of: 'of',
  blob: 'BLOB',
  emptyDb: 'This database has no tables to display.',
  invalidDb: 'This file could not be opened. Choose a valid SQLite database.',
  readError: 'The selected table could not be read.',
  fileTooLarge: 'This file is too large to open safely in the browser (100 MB maximum).',
})

const MAX_FILE_SIZE = 100 * 1024 * 1024
const PAGE_SIZE = 25

const input = ref(null)
// sql.js Database wraps WASM state and must not be deep-proxied by Vue.
const database = shallowRef(null)
const fileName = ref('')
const fileSize = ref(0)
const tables = ref([])
const selectedTable = ref('')
const columns = ref([])
const rows = ref([])
const search = ref('')
const currentPage = ref(1)
const totalRows = ref(0)
const loading = ref(false)
const error = ref('')
const dragging = ref(false)

const selectedTableInfo = computed(() =>
  tables.value.find(table => table.name === selectedTable.value)
)
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / PAGE_SIZE)))
const rangeStart = computed(() => totalRows.value ? (currentPage.value - 1) * PAGE_SIZE + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * PAGE_SIZE, totalRows.value))

const quoteIdentifier = (name) => `"${String(name).replaceAll('"', '""')}"`

const queryObjects = (sql, params = []) => {
  const statement = database.value.prepare(sql)
  try {
    statement.bind(params)
    const result = []
    while (statement.step()) result.push(statement.getAsObject())
    return result
  } finally {
    statement.free()
  }
}

const formatBytes = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatCell = (value, column) => {
  if (value === null || value === undefined) return { text: '—', kind: 'null' }
  if (value instanceof Uint8Array) {
    return { text: `${copy.value.blob} · ${formatBytes(value.byteLength)}`, kind: 'blob' }
  }
  if (typeof value === 'number' && ['last_updated', 'expires_at'].includes(column)) {
    if (value === 0) return { text: '—', kind: 'null', title: '0' }
    const date = new Date(value)
    if (!Number.isNaN(date.getTime())) {
      return { text: date.toLocaleString(isVi.value ? 'vi-VN' : 'en-US'), title: String(value), kind: 'date' }
    }
  }
  return { text: String(value), title: String(value), kind: typeof value }
}

const buildFilter = () => {
  const term = search.value.trim()
  if (!term) return { sql: '', params: [] }
  const searchable = columns.value.filter(column => !column.type.toUpperCase().includes('BLOB'))
  if (!searchable.length) return { sql: '', params: [] }
  return {
    sql: ` WHERE ${searchable.map(column => `CAST(${quoteIdentifier(column.name)} AS TEXT) LIKE ?`).join(' OR ')}`,
    params: searchable.map(() => `%${term}%`),
  }
}

const loadRows = () => {
  if (!database.value || !selectedTable.value) return
  error.value = ''
  try {
    const table = quoteIdentifier(selectedTable.value)
    const filter = buildFilter()
    totalRows.value = Number(queryObjects(`SELECT COUNT(*) AS count FROM ${table}${filter.sql}`, filter.params)[0]?.count || 0)
    if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
    const offset = (currentPage.value - 1) * PAGE_SIZE
    rows.value = queryObjects(
      `SELECT * FROM ${table}${filter.sql} LIMIT ? OFFSET ?`,
      [...filter.params, PAGE_SIZE, offset],
    )
  } catch (cause) {
    console.error(cause)
    rows.value = []
    totalRows.value = 0
    error.value = copy.value.readError
  }
}

const loadTable = (name) => {
  if (!database.value || !name) return
  selectedTable.value = name
  currentPage.value = 1
  search.value = ''
  const table = quoteIdentifier(name)
  columns.value = queryObjects(`PRAGMA table_info(${table})`).map(column => ({
    name: String(column.name),
    type: String(column.type || ''),
  }))
  loadRows()
}

const reset = () => {
  database.value?.close()
  database.value = null
  fileName.value = ''
  fileSize.value = 0
  tables.value = []
  selectedTable.value = ''
  columns.value = []
  rows.value = []
  search.value = ''
  currentPage.value = 1
  totalRows.value = 0
  error.value = ''
  if (input.value) input.value.value = ''
}

const openPicker = () => {
  if (!input.value) return
  input.value.value = ''
  input.value.click()
}

const openFile = async (file) => {
  if (!file || loading.value) return
  reset()
  if (file.size > MAX_FILE_SIZE) {
    error.value = copy.value.fileTooLarge
    return
  }

  loading.value = true
  try {
    const [{ default: initSqlJs }, { default: wasmUrl }] = await Promise.all([
      import('sql.js'),
      import('sql.js/dist/sql-wasm.wasm?url'),
    ])
    const SQL = await initSqlJs({ locateFile: () => wasmUrl })
    const bytes = new Uint8Array(await file.arrayBuffer())
    database.value = new SQL.Database(bytes)
    fileName.value = file.name
    fileSize.value = file.size

    const discovered = queryObjects(`
      SELECT name
      FROM sqlite_master
      WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
      ORDER BY name COLLATE NOCASE
    `).map(({ name }) => ({
      name: String(name),
      count: Number(queryObjects(`SELECT COUNT(*) AS count FROM ${quoteIdentifier(name)}`)[0]?.count || 0),
    }))

    tables.value = discovered
    if (!discovered.length) {
      reset()
      error.value = copy.value.emptyDb
      return
    }

    const preferred = discovered.find(table => table.name.toLowerCase().endsWith('enderchests')) || discovered[0]
    loadTable(preferred.name)
  } catch (cause) {
    console.error(cause)
    reset()
    error.value = copy.value.invalidDb
  } finally {
    loading.value = false
  }
}

const onInput = (event) => openFile(event.target.files?.[0])
const onDrop = (event) => {
  dragging.value = false
  openFile(event.dataTransfer?.files?.[0])
}

let searchTimer
watch(search, () => {
  clearTimeout(searchTimer)
  currentPage.value = 1
  searchTimer = setTimeout(loadRows, 180)
})
watch(currentPage, loadRows)

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  database.value?.close()
})
</script>

<template>
  <section class="sqlite-viewer" :class="{ 'is-compact': !database }" :aria-busy="loading">
    <input
      ref="input"
      class="sqlite-file-input"
      type="file"
      accept=".db,.sqlite,.sqlite3,application/vnd.sqlite3,application/x-sqlite3"
      @change="onInput"
    >

    <div
      v-if="!database"
      class="sqlite-dropzone"
      :class="{ dragging }"
      role="button"
      tabindex="0"
      @click="openPicker"
      @keydown.enter.prevent="openPicker"
      @keydown.space.prevent="openPicker"
      @dragenter.prevent="dragging = true"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="sqlite-dropzone-icon"><LucideIcon name="Database" :size="28" /></span>
      <strong>{{ copy.dropTitle }}</strong>
      <span>{{ copy.dropHint }}</span>
      <button type="button" class="sqlite-primary-button" :disabled="loading" @click.stop="openPicker">
        {{ loading ? copy.loading : copy.choose }}
      </button>
      <small>{{ copy.privacy }}</small>
    </div>

    <p v-if="error" class="sqlite-error" role="alert">{{ error }}</p>

    <div v-if="database && selectedTable" class="sqlite-workspace">
      <header class="sqlite-file-bar">
        <div>
          <strong>{{ fileName }}</strong>
          <span>{{ formatBytes(fileSize) }}</span>
        </div>
        <div class="sqlite-file-actions">
          <button type="button" @click="openPicker">{{ copy.replace }}</button>
          <button type="button" @click="reset">{{ copy.close }}</button>
        </div>
      </header>

      <div class="sqlite-browser">
        <aside class="sqlite-tables" :aria-label="copy.tables">
          <strong class="sqlite-tables-heading">{{ copy.tables }}</strong>
          <button
            v-for="table in tables"
            :key="table.name"
            type="button"
            :class="{ active: table.name === selectedTable }"
            :aria-current="table.name === selectedTable ? 'page' : undefined"
            @click="loadTable(table.name)"
          >
            <span>{{ table.name }}</span>
            <small>{{ table.count }}</small>
          </button>
        </aside>

        <div class="sqlite-data">
          <div class="sqlite-toolbar">
            <div class="sqlite-table-title">
              <strong>{{ selectedTableInfo?.name }}</strong>
              <span>{{ totalRows.toLocaleString(isVi ? 'vi-VN' : 'en-US') }} {{ copy.rows }}</span>
            </div>
            <label class="sqlite-search">
              <LucideIcon name="Search" :size="16" />
              <input v-model="search" type="search" :placeholder="copy.search">
            </label>
          </div>

          <div class="sqlite-table-scroll">
            <table v-if="rows.length" class="sqlite-result-table">
              <thead>
                <tr>
                  <th v-for="column in columns" :key="column.name" scope="col">
                    <span>{{ column.name }}</span>
                    <small v-if="column.type">{{ column.type }}</small>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
                  <td v-for="column in columns" :key="column.name">
                    <span
                      :class="`sqlite-cell-${formatCell(row[column.name], column.name).kind}`"
                      :title="formatCell(row[column.name], column.name).title"
                    >{{ formatCell(row[column.name], column.name).text }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="sqlite-empty">{{ copy.noRows }}</div>
          </div>

          <footer class="sqlite-pagination">
            <span>{{ rangeStart }}–{{ rangeEnd }} / {{ totalRows.toLocaleString(isVi ? 'vi-VN' : 'en-US') }}</span>
            <div>
              <button type="button" :disabled="currentPage <= 1" @click="currentPage--">{{ copy.previous }}</button>
              <span>{{ copy.page }} {{ currentPage }} {{ copy.of }} {{ totalPages }}</span>
              <button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">{{ copy.next }}</button>
            </div>
          </footer>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.sqlite-viewer {
  position: relative;
  left: 50%;
  width: min(1500px, calc(100vw - 48px));
  margin: 24px 0;
  color: var(--vp-c-text-1);
  transform: translateX(-50%);
  transition: width 0.2s ease;
}

/* Before a file is chosen there's nothing that needs the wide breakout width
   (that's only useful once the table browser has real columns to show), so a
   narrower, centered card reads as an intentional call-to-action instead of a
   mostly-empty box stretched across the page. Scoped to the same breakpoint
   as the mobile override below so specificity (two classes vs. one) can't
   fight it there — mobile always wants the plain 100%-width block layout. */
@media (min-width: 761px) {
  .sqlite-viewer.is-compact {
    width: min(640px, calc(100vw - 48px));
  }
}

.sqlite-file-input {
  position: fixed;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.sqlite-dropzone {
  display: flex;
  min-height: 240px;
  padding: 32px;
  border: 1.5px dashed var(--vp-c-divider);
  border-radius: 16px;
  background: var(--vp-c-bg-soft);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s, transform 0.2s;
}

.sqlite-dropzone:hover,
.sqlite-dropzone:focus-visible,
.sqlite-dropzone.dragging {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  outline: none;
}

.sqlite-dropzone.dragging { transform: scale(1.005); }
.sqlite-dropzone strong { margin-top: 14px; font-size: 1.1rem; }
.sqlite-dropzone > span:not(.sqlite-dropzone-icon) { margin-top: 5px; color: var(--vp-c-text-2); }
.sqlite-dropzone small { margin-top: 14px; color: var(--vp-c-text-3); }

.sqlite-dropzone-icon {
  display: grid;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  place-items: center;
}

.sqlite-primary-button,
.sqlite-file-actions button,
.sqlite-pagination button {
  border: 1px solid var(--vp-c-divider);
  border-radius: 9px;
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  font: inherit;
  cursor: pointer;
}

.sqlite-primary-button {
  margin-top: 20px;
  padding: 9px 16px;
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
  font-weight: 600;
}

.sqlite-error {
  padding: 12px 14px;
  border: 1px solid var(--vp-c-danger-2);
  border-radius: 10px;
  background: var(--vp-c-danger-soft);
  color: var(--vp-c-danger-1);
}

.sqlite-workspace {
  overflow: hidden;
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  background: var(--vp-c-bg);
}

.sqlite-file-bar,
.sqlite-toolbar,
.sqlite-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.sqlite-file-bar {
  min-height: 62px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.sqlite-file-bar > div:first-child { display: flex; min-width: 0; flex-direction: column; }
.sqlite-file-bar strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-file-bar span { color: var(--vp-c-text-3); font-size: 0.78rem; }
.sqlite-file-actions { display: flex; gap: 8px; }
.sqlite-file-actions button { padding: 6px 10px; font-size: 0.8rem; }

.sqlite-browser { display: grid; min-height: 430px; grid-template-columns: 190px minmax(0, 1fr); }

.sqlite-tables {
  padding: 12px 8px;
  border-right: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.sqlite-tables-heading {
  display: block;
  padding: 4px 9px 9px;
  color: var(--vp-c-text-3);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sqlite-tables button {
  display: flex;
  width: 100%;
  padding: 8px 9px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--vp-c-text-2);
  font: inherit;
  font-size: 0.82rem;
  text-align: left;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
}

.sqlite-tables button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-tables button small { color: var(--vp-c-text-3); }
.sqlite-tables button:hover,
.sqlite-tables button.active { background: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); }

.sqlite-data { display: flex; min-width: 0; flex-direction: column; }
.sqlite-toolbar { min-height: 66px; padding: 10px 14px; border-bottom: 1px solid var(--vp-c-divider); }
.sqlite-table-title { display: flex; min-width: 0; flex-direction: column; }
.sqlite-table-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-table-title span { color: var(--vp-c-text-3); font-size: 0.78rem; }

.sqlite-search {
  display: flex;
  width: min(260px, 45%);
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 9px;
  color: var(--vp-c-text-3);
  align-items: center;
  gap: 7px;
}

.sqlite-search:focus-within { border-color: var(--vp-c-brand-1); }
.sqlite-search input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--vp-c-text-1); font: inherit; font-size: 0.82rem; }

.sqlite-table-scroll { overflow: auto; min-height: 305px; flex: 1; }
.sqlite-result-table { width: max-content; min-width: 100%; margin: 0; border-collapse: collapse; font-size: 0.79rem; }
.sqlite-result-table th,
.sqlite-result-table td { max-width: 280px; padding: 9px 12px; border: 0; border-right: 1px solid var(--vp-c-divider); border-bottom: 1px solid var(--vp-c-divider); text-align: left; white-space: nowrap; }
.sqlite-result-table th { position: sticky; top: 0; z-index: 1; background: var(--vp-c-bg-soft); color: var(--vp-c-text-2); font-weight: 600; }
.sqlite-result-table th span,
.sqlite-result-table th small { display: block; }
.sqlite-result-table th small { margin-top: 1px; color: var(--vp-c-text-3); font-size: 0.64rem; font-weight: 500; }
.sqlite-result-table td > span { display: block; overflow: hidden; text-overflow: ellipsis; }
.sqlite-result-table tbody tr:hover { background: var(--vp-c-default-soft); }
.sqlite-cell-null { color: var(--vp-c-text-3); }
.sqlite-cell-blob { padding: 2px 6px; border-radius: 5px; background: var(--vp-c-default-soft); color: var(--vp-c-text-2); font-family: var(--vp-font-family-mono); font-size: 0.72rem; }
.sqlite-cell-date { font-variant-numeric: tabular-nums; }
.sqlite-empty { display: grid; min-height: 305px; color: var(--vp-c-text-3); font-size: 0.86rem; place-items: center; }

.sqlite-pagination { min-height: 54px; padding: 8px 14px; border-top: 1px solid var(--vp-c-divider); color: var(--vp-c-text-3); font-size: 0.78rem; }
.sqlite-pagination > div { display: flex; align-items: center; gap: 10px; }
.sqlite-pagination button { padding: 5px 9px; font-size: 0.76rem; }
.sqlite-pagination button:hover:not(:disabled) { border-color: var(--vp-c-brand-1); color: var(--vp-c-brand-1); }
.sqlite-pagination button:disabled,
.sqlite-primary-button:disabled { cursor: not-allowed; opacity: 0.45; }

@media (max-width: 760px) {
  .sqlite-viewer {
    position: static;
    width: 100%;
    transform: none;
  }
  .sqlite-dropzone { min-height: 260px; padding: 24px 16px; }
  .sqlite-browser { display: block; }
  .sqlite-tables { display: flex; overflow-x: auto; padding: 8px; border-right: 0; border-bottom: 1px solid var(--vp-c-divider); gap: 5px; }
  .sqlite-tables-heading { display: none; }
  .sqlite-tables button { width: auto; min-width: max-content; gap: 12px; }
  .sqlite-toolbar { align-items: stretch; flex-direction: column; }
  .sqlite-search { width: 100%; }
  .sqlite-pagination { align-items: flex-start; flex-direction: column; }
  .sqlite-pagination > div { width: 100%; justify-content: space-between; }
  .sqlite-file-actions button:first-child { display: none; }
}
</style>
