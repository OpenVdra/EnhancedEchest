<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useData } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'
import {
  clearTransientSqliteSession,
  getTransientSqliteSession,
  setTransientSqliteSession,
} from './transientSession.js'

const { lang } = useData()
const isVi = computed(() => lang.value.startsWith('vi'))

const copy = computed(() => isVi.value ? {
  dropTitle: 'Chọn cơ sở dữ liệu SQLite',
  dropHint: 'Kéo thả file vào đây hoặc chọn enderchests.db',
  choose: 'Chọn file SQLite',
  privacy: 'File chỉ được xử lý trong trình duyệt và không được tải lên máy chủ.',
  loading: 'Đang mở cơ sở dữ liệu…',
  replace: 'Đổi file',
  download: 'Tải file .db',
  close: 'Đóng',
  modified: 'Có thay đổi chưa tải xuống',
  editHint: 'Bấm vào một ô để sửa. Ô BLOB chỉ đọc — bấm để xem hoặc tải về.',
  saveEdit: 'Lưu thay đổi',
  cancelEdit: 'Hủy',
  setNull: 'Đặt NULL',
  required: 'bắt buộc',
  tables: 'Bảng',
  rows: 'hàng',
  search: 'Tìm trong bảng…',
  noRows: 'Không tìm thấy dữ liệu phù hợp.',
  previous: 'Trước',
  next: 'Sau',
  page: 'Trang',
  of: '/',
  blob: 'BLOB',
  blobOpen: 'Bấm để xem hoặc tải dữ liệu BLOB',
  blobTitle: 'Nội dung BLOB',
  blobDownload: 'Tải BLOB',
  blobCopy: 'Sao chép dạng hex',
  blobCopied: 'Đã sao chép',
  blobHex: 'Hex',
  blobText: 'Văn bản',
  blobEmpty: 'Ô BLOB này không có dữ liệu.',
  blobTruncated: (shown, total) => `Chỉ hiển thị ${shown} byte đầu trong tổng số ${total} byte. Tải file xuống để xem toàn bộ.`,
  emptyDb: 'Cơ sở dữ liệu không có bảng nào để hiển thị.',
  invalidDb: 'Không thể mở file này. Hãy chọn một cơ sở dữ liệu SQLite hợp lệ.',
  readError: 'Không thể đọc dữ liệu từ bảng đã chọn.',
  updateError: 'Không thể cập nhật ô này.',
  invalidNumber: 'Giá trị này phải là một số hợp lệ.',
  nullNotAllowed: 'Cột này bắt buộc phải có giá trị và không thể đặt thành NULL.',
  uniqueViolation: 'Giá trị này bị trùng với một hàng khác hoặc tạo ra khóa đã tồn tại.',
  checkViolation: 'Giá trị này không đáp ứng quy tắc dữ liệu của bảng.',
  foreignKeyViolation: 'Giá trị này không tham chiếu đến một hàng hợp lệ trong bảng liên quan.',
  typeMismatch: 'Giá trị này không đúng kiểu dữ liệu mà cột yêu cầu.',
  fileTooLarge: 'File quá lớn để mở an toàn trong trình duyệt (tối đa 100 MB).',
} : {
  dropTitle: 'Choose a SQLite database',
  dropHint: 'Drop a file here or select enderchests.db',
  choose: 'Choose SQLite file',
  privacy: 'The file is processed only in your browser and is never uploaded.',
  loading: 'Opening database…',
  replace: 'Change file',
  download: 'Download .db',
  close: 'Close',
  modified: 'Changes not downloaded',
  editHint: 'Click a cell to edit it. BLOB cells are read-only — click one to view or download it.',
  saveEdit: 'Save change',
  cancelEdit: 'Cancel',
  setNull: 'Set NULL',
  required: 'required',
  tables: 'Tables',
  rows: 'rows',
  search: 'Search this table…',
  noRows: 'No matching data found.',
  previous: 'Previous',
  next: 'Next',
  page: 'Page',
  of: 'of',
  blob: 'BLOB',
  blobOpen: 'Click to view or download this blob',
  blobTitle: 'Blob contents',
  blobDownload: 'Download blob',
  blobCopy: 'Copy as hex',
  blobCopied: 'Copied',
  blobHex: 'Hex',
  blobText: 'Text',
  blobEmpty: 'This blob cell holds no data.',
  blobTruncated: (shown, total) => `Showing the first ${shown} of ${total} bytes. Download the file to see all of it.`,
  emptyDb: 'This database has no tables to display.',
  invalidDb: 'This file could not be opened. Choose a valid SQLite database.',
  readError: 'The selected table could not be read.',
  updateError: 'This cell could not be updated.',
  invalidNumber: 'This value must be a valid number.',
  nullNotAllowed: 'This column requires a value and cannot be set to NULL.',
  uniqueViolation: 'This value duplicates another row or creates a key that already exists.',
  checkViolation: 'This value does not satisfy the table\'s data rules.',
  foreignKeyViolation: 'This value does not reference a valid row in the related table.',
  typeMismatch: 'This value does not match the data type required by the column.',
  fileTooLarge: 'This file is too large to open safely in the browser (100 MB maximum).',
})

const MAX_FILE_SIZE = 100 * 1024 * 1024
const PAGE_SIZE = 25
// A chest blob is a few kilobytes, but nothing stops a foreign database from
// storing megabytes in one cell — rendering that as hex would freeze the tab,
// so the preview stops here and the download stays the way to get everything.
const BLOB_PREVIEW_BYTES = 4096
const HEX_COLUMNS = 16

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
const dirty = ref(false)
const editingCell = ref(null)
const editError = ref('')
const tableHasRowid = ref(true)
const rowidAlias = ref('__sqlite_editor_rowid__')
// The bytes are held raw: a Uint8Array behind a normal ref would still be
// skipped by Vue's reactivity, but shallowRef makes that guarantee explicit
// for a value that can reach tens of megabytes.
const blobView = shallowRef(null)
const blobMode = ref('hex')
const blobCopied = ref(false)
const blobDialog = ref(null)

const selectedTableInfo = computed(() =>
  tables.value.find(table => table.name === selectedTable.value)
)
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / PAGE_SIZE)))
const rangeStart = computed(() => totalRows.value ? (currentPage.value - 1) * PAGE_SIZE + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * PAGE_SIZE, totalRows.value))

const quoteIdentifier = (name) => `"${String(name).replaceAll('"', '""')}"`

const loadSqlJs = async () => {
  const [{ default: initSqlJs }, { default: wasmUrl }] = await Promise.all([
    import('sql.js'),
    import('sql.js/dist/sql-wasm.wasm?url'),
  ])
  return initSqlJs({ locateFile: () => wasmUrl })
}

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
  // The rows behind an open preview are about to be replaced, so keeping the
  // dialog up would show bytes that no longer belong to anything on screen.
  blobView.value = null
  try {
    const table = quoteIdentifier(selectedTable.value)
    const filter = buildFilter()
    totalRows.value = Number(queryObjects(`SELECT COUNT(*) AS count FROM ${table}${filter.sql}`, filter.params)[0]?.count || 0)
    if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
    const offset = (currentPage.value - 1) * PAGE_SIZE
    const select = tableHasRowid.value
      ? `SELECT rowid AS ${quoteIdentifier(rowidAlias.value)}, *`
      : 'SELECT *'
    rows.value = queryObjects(
      `${select} FROM ${table}${filter.sql} LIMIT ? OFFSET ?`,
      [...filter.params, PAGE_SIZE, offset],
    ).map(values => ({
      identity: tableHasRowid.value
        ? { rowid: values[rowidAlias.value] }
        : {
            primaryKey: columns.value
              .filter(column => column.primaryKey > 0)
              .sort((a, b) => a.primaryKey - b.primaryKey)
              .map(column => ({ name: column.name, value: values[column.name] })),
          },
      values,
    }))
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
    primaryKey: Number(column.pk || 0),
    notNull: Boolean(column.notnull),
  }))
  let alias = '__sqlite_editor_rowid__'
  while (columns.value.some(column => column.name === alias)) alias += '_'
  rowidAlias.value = alias
  const createSql = queryObjects(
    `SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?`,
    [name],
  )[0]?.sql
  tableHasRowid.value = !/\bWITHOUT\s+ROWID\b/i.test(String(createSql || ''))
  editingCell.value = null
  editError.value = ''
  loadRows()
}

const reset = ({ clearSession = true } = {}) => {
  if (clearSession) clearTransientSqliteSession()
  blobView.value = null
  blobCopied.value = false
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
  dirty.value = false
  editingCell.value = null
  editError.value = ''
  if (input.value) input.value.value = ''
}

const discoverTables = () => queryObjects(`
  SELECT name
  FROM sqlite_master
  WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
  ORDER BY name COLLATE NOCASE
`).map(({ name }) => ({
  name: String(name),
  count: Number(queryObjects(`SELECT COUNT(*) AS count FROM ${quoteIdentifier(name)}`)[0]?.count || 0),
}))

const openBytes = async (bytes, metadata) => {
  const SQL = await loadSqlJs()
  database.value = new SQL.Database(bytes)
  fileName.value = metadata.fileName
  fileSize.value = metadata.fileSize
  dirty.value = Boolean(metadata.dirty)
  tables.value = discoverTables()

  if (!tables.value.length) throw new Error('empty-database')
  const fallback = tables.value.find(table => table.name.toLowerCase().endsWith('enderchests')) || tables.value[0]
  const selected = tables.value.find(table => table.name === metadata.selectedTable) || fallback
  loadTable(selected.name)
  search.value = metadata.search || ''
  await nextTick()
  currentPage.value = Math.max(1, Number(metadata.currentPage) || 1)
  await nextTick()
  loadRows()
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
    const bytes = new Uint8Array(await file.arrayBuffer())
    await openBytes(bytes, { fileName: file.name, fileSize: file.size })
  } catch (cause) {
    console.error(cause)
    reset()
    error.value = cause.message === 'empty-database' ? copy.value.emptyDb : copy.value.invalidDb
  } finally {
    loading.value = false
  }
}

const isBlobColumn = (column) => column.type.toUpperCase().includes('BLOB')
const isBlobValue = (value) => value instanceof Uint8Array
const canSetNull = (column) => !column.notNull && column.primaryKey === 0

const sanitizeFilePart = (value) => String(value).replace(/[^\w.-]+/g, '_').replace(/^_+|_+$/g, '') || 'value'

const rowLabel = (row, rowIndex) => {
  if (Object.hasOwn(row.identity, 'rowid')) return `row${row.identity.rowid}`
  const key = row.identity.primaryKey?.map(part => part.value).filter(value => !isBlobValue(value)).join('-')
  return key ? sanitizeFilePart(key) : `row${(currentPage.value - 1) * PAGE_SIZE + rowIndex + 1}`
}

const openBlobView = (rowIndex, column) => {
  const row = rows.value[rowIndex]
  const value = row?.values[column.name]
  if (!isBlobValue(value)) return
  cancelEdit()
  blobCopied.value = false
  blobMode.value = 'hex'
  const base = sanitizeFilePart((fileName.value || 'database').replace(/\.[^.]+$/, ''))
  blobView.value = {
    bytes: value,
    column: column.name,
    table: selectedTable.value,
    downloadName: `${base}-${sanitizeFilePart(selectedTable.value)}-${sanitizeFilePart(column.name)}-${rowLabel(row, rowIndex)}.bin`,
  }
}

const closeBlobView = () => {
  blobView.value = null
  blobCopied.value = false
}

const blobPreviewBytes = computed(() => {
  const bytes = blobView.value?.bytes
  if (!bytes) return null
  return bytes.byteLength > BLOB_PREVIEW_BYTES ? bytes.subarray(0, BLOB_PREVIEW_BYTES) : bytes
})

const blobHexLines = computed(() => {
  const bytes = blobPreviewBytes.value
  if (!bytes) return []
  const lines = []
  for (let offset = 0; offset < bytes.byteLength; offset += HEX_COLUMNS) {
    const chunk = bytes.subarray(offset, offset + HEX_COLUMNS)
    const hex = Array.from(chunk, byte => byte.toString(16).padStart(2, '0'))
    lines.push({
      offset: offset.toString(16).padStart(8, '0'),
      hex: `${hex.join(' ')}${'   '.repeat(HEX_COLUMNS - chunk.length)}`,
      ascii: Array.from(chunk, byte => (byte >= 0x20 && byte < 0x7f ? String.fromCharCode(byte) : '·')).join(''),
    })
  }
  return lines
})

const blobText = computed(() => {
  const bytes = blobPreviewBytes.value
  if (!bytes) return ''
  // Chest blobs are compressed NBT, so most of this is unreadable — the point
  // is to surface the plain-text fragments (item ids, custom names) that are.
  return new TextDecoder('utf-8').decode(bytes).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '·')
})

const downloadBlob = () => {
  const view = blobView.value
  if (!view) return
  const blob = new Blob([view.bytes], { type: 'application/octet-stream' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = view.downloadName
  link.hidden = true
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

let copiedTimer
const copyBlobHex = async () => {
  const bytes = blobPreviewBytes.value
  if (!bytes) return
  try {
    await navigator.clipboard.writeText(Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join(''))
    blobCopied.value = true
    clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => { blobCopied.value = false }, 1600)
  } catch (cause) {
    console.error(cause)
  }
}

const startEdit = (rowIndex, column) => {
  if (isBlobColumn(column)) return
  const value = rows.value[rowIndex]?.values[column.name]
  if (value instanceof Uint8Array) return
  editError.value = ''
  editingCell.value = {
    rowIndex,
    column,
    draft: value === null || value === undefined ? '' : String(value),
  }
}

const cancelEdit = () => {
  editingCell.value = null
  editError.value = ''
}

const parseEditedValue = (draft, column) => {
  const type = column.type.toUpperCase()
  if (type.includes('INT')) {
    if (!/^[+-]?\d+$/.test(draft.trim())) throw new Error('invalid-number')
    return Number(draft)
  }
  if (/(REAL|FLOA|DOUB)/.test(type)) {
    const value = Number(draft)
    if (!draft.trim() || !Number.isFinite(value)) throw new Error('invalid-number')
    return value
  }
  return draft
}

const saveEdit = (asNull = false) => {
  const edit = editingCell.value
  const row = rows.value[edit?.rowIndex]
  if (!edit || !row || !database.value) return
  if (asNull && !canSetNull(edit.column)) {
    editError.value = copy.value.nullNotAllowed
    return
  }

  try {
    const value = asNull ? null : parseEditedValue(edit.draft, edit.column)
    const table = quoteIdentifier(selectedTable.value)
    const column = quoteIdentifier(edit.column.name)
    let where
    let identityParams
    if (Object.hasOwn(row.identity, 'rowid')) {
      where = 'rowid IS ?'
      identityParams = [row.identity.rowid]
    } else if (row.identity.primaryKey?.length) {
      where = row.identity.primaryKey.map(key => `${quoteIdentifier(key.name)} IS ?`).join(' AND ')
      identityParams = row.identity.primaryKey.map(key => key.value)
    } else {
      throw new Error('missing-row-identity')
    }

    database.value.run('BEGIN')
    database.value.run(`UPDATE ${table} SET ${column} = ? WHERE ${where}`, [value, ...identityParams])
    if (database.value.getRowsModified() !== 1) throw new Error('unexpected-update-count')
    database.value.run('COMMIT')
    dirty.value = true
    cancelEdit()
    loadRows()
  } catch (cause) {
    try { database.value.run('ROLLBACK') } catch { /* no active transaction */ }
    console.error(cause)
    const message = String(cause.message || '')
    if (message === 'invalid-number') editError.value = copy.value.invalidNumber
    else if (/NOT NULL constraint failed/i.test(message)) editError.value = copy.value.nullNotAllowed
    else if (/UNIQUE constraint failed/i.test(message)) editError.value = copy.value.uniqueViolation
    else if (/CHECK constraint failed/i.test(message)) editError.value = copy.value.checkViolation
    else if (/FOREIGN KEY constraint failed/i.test(message)) editError.value = copy.value.foreignKeyViolation
    else if (/datatype mismatch/i.test(message)) editError.value = copy.value.typeMismatch
    else editError.value = copy.value.updateError
  }
}

const downloadDatabase = () => {
  if (!database.value) return
  const bytes = database.value.export()
  const blob = new Blob([bytes], { type: 'application/vnd.sqlite3' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName.value || 'database.db'
  link.hidden = true
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
  fileSize.value = bytes.byteLength
  dirty.value = false
}

const restoreSession = async () => {
  const session = getTransientSqliteSession()
  if (!session) return
  loading.value = true
  try {
    await openBytes(session.bytes, session)
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

// The dialog is teleported to <body>, so Escape has to be caught on the
// document rather than on an ancestor of the editor.
const onWindowKeydown = (event) => {
  if (event.key === 'Escape' && blobView.value) closeBlobView()
}

watch(blobView, async (view) => {
  if (typeof document === 'undefined') return
  document.body.style.overflow = view ? 'hidden' : ''
  if (!view) return
  await nextTick()
  blobDialog.value?.focus()
})

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
  restoreSession()
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  clearTimeout(copiedTimer)
  window.removeEventListener('keydown', onWindowKeydown)
  if (typeof document !== 'undefined') document.body.style.overflow = ''
  if (database.value) {
    setTransientSqliteSession({
      bytes: database.value.export(),
      fileName: fileName.value,
      fileSize: fileSize.value,
      selectedTable: selectedTable.value,
      search: search.value,
      currentPage: currentPage.value,
      dirty: dirty.value,
    })
  }
  database.value?.close()
})
</script>

<template>
  <section class="sqlite-editor" :class="{ 'is-compact': !database }" :aria-busy="loading">
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
          <span>
            {{ formatBytes(fileSize) }}
            <em v-if="dirty" class="sqlite-modified">{{ copy.modified }}</em>
          </span>
        </div>
        <div class="sqlite-file-actions">
          <button type="button" class="sqlite-download-button" @click="downloadDatabase">
            <LucideIcon name="Download" :size="14" />
            {{ copy.download }}
          </button>
          <button type="button" class="sqlite-ghost-button" @click="openPicker">
            <LucideIcon name="RefreshCw" :size="14" />
            {{ copy.replace }}
          </button>
          <button type="button" class="sqlite-ghost-button" @click="reset">
            <LucideIcon name="X" :size="14" />
            {{ copy.close }}
          </button>
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
              <span>
                {{ totalRows.toLocaleString(isVi ? 'vi-VN' : 'en-US') }} {{ copy.rows }}
                · {{ copy.editHint }}
              </span>
            </div>
            <label class="sqlite-search">
              <LucideIcon name="Search" :size="16" />
              <input v-model="search" type="search" :placeholder="copy.search">
            </label>
          </div>

          <div class="sqlite-pagination">
            <span>{{ rangeStart }}–{{ rangeEnd }} / {{ totalRows.toLocaleString(isVi ? 'vi-VN' : 'en-US') }}</span>
            <div>
              <button type="button" :aria-label="copy.previous" :title="copy.previous" :disabled="currentPage <= 1" @click="currentPage--">
                <LucideIcon name="ChevronLeft" :size="15" />
              </button>
              <span>{{ copy.page }} {{ currentPage }} {{ copy.of }} {{ totalPages }}</span>
              <button type="button" :aria-label="copy.next" :title="copy.next" :disabled="currentPage >= totalPages" @click="currentPage++">
                <LucideIcon name="ChevronRight" :size="15" />
              </button>
            </div>
          </div>

          <div class="sqlite-table-scroll">
            <table v-if="rows.length" class="sqlite-result-table">
              <thead>
                <tr>
                  <th v-for="column in columns" :key="column.name" scope="col">
                    <span>{{ column.name }}</span>
                    <small v-if="column.type">
                      {{ column.type }}<template v-if="!canSetNull(column)"> · {{ copy.required }}</template>
                    </small>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
                  <td v-for="column in columns" :key="column.name">
                    <div
                      v-if="editingCell?.rowIndex === rowIndex && editingCell?.column.name === column.name"
                      class="sqlite-cell-editor"
                    >
                      <div class="sqlite-cell-editor-controls">
                        <input
                          v-model="editingCell.draft"
                          autofocus
                          :aria-label="column.name"
                          :aria-invalid="Boolean(editError)"
                          @input="editError = ''"
                          @keydown.enter.prevent="saveEdit()"
                          @keydown.escape.prevent="cancelEdit"
                        >
                        <button type="button" class="sqlite-cell-editor-confirm" :title="copy.saveEdit" @click="saveEdit()">
                          <LucideIcon name="Check" :size="13" />
                        </button>
                        <button
                          type="button"
                          :disabled="!canSetNull(column)"
                          :title="canSetNull(column) ? copy.setNull : copy.nullNotAllowed"
                          @click="saveEdit(true)"
                        >NULL</button>
                        <button type="button" :title="copy.cancelEdit" @click="cancelEdit">
                          <LucideIcon name="X" :size="13" />
                        </button>
                      </div>
                      <small v-if="editError" class="sqlite-cell-edit-error" role="alert">{{ editError }}</small>
                      <small v-else-if="!canSetNull(column)" class="sqlite-cell-edit-note">{{ copy.nullNotAllowed }}</small>
                    </div>
                    <button
                      v-else-if="isBlobValue(row.values[column.name])"
                      type="button"
                      class="sqlite-cell-blob"
                      :title="copy.blobOpen"
                      @click="openBlobView(rowIndex, column)"
                    >
                      <LucideIcon name="Binary" :size="12" />
                      {{ formatCell(row.values[column.name], column.name).text }}
                    </button>
                    <span
                      v-else
                      :class="[
                        `sqlite-cell-${formatCell(row.values[column.name], column.name).kind}`,
                        { 'sqlite-cell-editable': !isBlobColumn(column) },
                      ]"
                      :title="formatCell(row.values[column.name], column.name).title"
                      :tabindex="isBlobColumn(column) ? undefined : 0"
                      @click="startEdit(rowIndex, column)"
                      @keydown.enter.prevent="startEdit(rowIndex, column)"
                    >{{ formatCell(row.values[column.name], column.name).text }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="sqlite-empty">{{ copy.noRows }}</div>
          </div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="blobView"
        class="sqlite-blob-overlay"
        @click.self="closeBlobView"
      >
        <div
          ref="blobDialog"
          class="sqlite-blob-dialog"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          :aria-label="copy.blobTitle"
        >
          <header class="sqlite-blob-header">
            <div>
              <strong>{{ blobView.table }}.{{ blobView.column }}</strong>
              <span>{{ copy.blob }} · {{ formatBytes(blobView.bytes.byteLength) }}</span>
            </div>
            <div class="sqlite-blob-actions">
              <button type="button" class="sqlite-download-button" @click="downloadBlob">
                <LucideIcon name="Download" :size="14" />
                {{ copy.blobDownload }}
              </button>
              <button type="button" class="sqlite-ghost-button" :disabled="!blobView.bytes.byteLength" @click="copyBlobHex">
                <LucideIcon :name="blobCopied ? 'Check' : 'Copy'" :size="14" />
                {{ blobCopied ? copy.blobCopied : copy.blobCopy }}
              </button>
              <button type="button" class="sqlite-ghost-button" :title="copy.close" @click="closeBlobView">
                <LucideIcon name="X" :size="14" />
              </button>
            </div>
          </header>

          <div class="sqlite-blob-modes">
            <button type="button" :class="{ active: blobMode === 'hex' }" @click="blobMode = 'hex'">{{ copy.blobHex }}</button>
            <button type="button" :class="{ active: blobMode === 'text' }" @click="blobMode = 'text'">{{ copy.blobText }}</button>
          </div>

          <div class="sqlite-blob-body">
            <p v-if="!blobView.bytes.byteLength" class="sqlite-blob-empty">{{ copy.blobEmpty }}</p>
            <pre v-else-if="blobMode === 'hex'" class="sqlite-blob-hex"><span
              v-for="line in blobHexLines"
              :key="line.offset"
              class="sqlite-blob-line"
            ><i>{{ line.offset }}</i>{{ line.hex }}<b>{{ line.ascii }}</b></span></pre>
            <pre v-else class="sqlite-blob-text">{{ blobText }}</pre>
          </div>

          <footer v-if="blobView.bytes.byteLength > BLOB_PREVIEW_BYTES" class="sqlite-blob-footer">
            {{ copy.blobTruncated(BLOB_PREVIEW_BYTES, blobView.bytes.byteLength) }}
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.sqlite-editor {
  --sqlite-line: color-mix(in srgb, var(--vp-c-text-1) 10%, transparent);
  --sqlite-line-soft: color-mix(in srgb, var(--vp-c-text-1) 6%, transparent);
  --sqlite-surface: color-mix(in srgb, var(--vp-c-bg-soft) 88%, transparent);
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
  .sqlite-editor.is-compact {
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
  border: 1.5px dashed var(--sqlite-line);
  border-radius: 20px;
  background: var(--sqlite-surface);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s, box-shadow 0.2s;
}

.sqlite-dropzone:hover,
.sqlite-dropzone:focus-visible,
.sqlite-dropzone.dragging {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.1);
  outline: none;
}

.sqlite-dropzone strong { margin-top: 16px; font-size: 1.1rem; }
.sqlite-dropzone > span:not(.sqlite-dropzone-icon) { margin-top: 5px; color: var(--vp-c-text-2); }
.sqlite-dropzone small { margin-top: 16px; color: var(--vp-c-text-3); }

.sqlite-dropzone-icon {
  display: grid;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  place-items: center;
}

.sqlite-primary-button,
.sqlite-file-actions button,
.sqlite-pagination button {
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--vp-c-text-1);
  font: inherit;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}

.sqlite-primary-button {
  margin-top: 22px;
  padding: 10px 18px;
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
  font-weight: 600;
}

.sqlite-primary-button:hover:not(:disabled) { background: var(--vp-c-brand-2); }

.sqlite-error {
  padding: 12px 14px;
  border: 1px solid var(--vp-c-danger-2);
  border-radius: 12px;
  background: var(--vp-c-danger-soft);
  color: var(--vp-c-danger-1);
}

.sqlite-workspace {
  overflow: hidden;
  border: 1px solid var(--sqlite-line);
  border-radius: 18px;
  background: var(--vp-c-bg);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.1);
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
  padding: 10px 18px;
  border-bottom: 1px solid var(--sqlite-line-soft);
}

.sqlite-file-bar > div:first-child { display: flex; min-width: 0; flex-direction: column; }
.sqlite-file-bar strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-file-bar span { color: var(--vp-c-text-3); font-size: 0.78rem; }
.sqlite-file-actions { display: flex; gap: 4px; }
.sqlite-file-actions button { display: inline-flex; padding: 7px 11px; font-size: 0.8rem; align-items: center; gap: 6px; }
.sqlite-ghost-button:hover { background: var(--vp-c-bg-mute); }
.sqlite-file-actions .sqlite-download-button { color: var(--vp-c-brand-1); font-weight: 600; }
.sqlite-file-actions .sqlite-download-button:hover { background: var(--vp-c-brand-soft); }
.sqlite-modified { margin-left: 7px; color: var(--vp-c-warning-1); font-style: normal; }
.sqlite-modified::before { content: '•'; margin-right: 5px; }

.sqlite-browser { display: grid; min-height: 430px; grid-template-columns: 190px minmax(0, 1fr); }

.sqlite-tables {
  padding: 14px 10px;
  border-right: 1px solid var(--sqlite-line-soft);
  background: var(--sqlite-surface);
}

.sqlite-tables-heading {
  display: block;
  padding: 4px 10px 10px;
  color: var(--vp-c-text-3);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sqlite-tables button {
  display: flex;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--vp-c-text-2);
  font: inherit;
  font-size: 0.82rem;
  text-align: left;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.sqlite-tables button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-tables button small { color: var(--vp-c-text-3); }
.sqlite-tables button:hover { background: var(--vp-c-bg-mute); }
.sqlite-tables button.active { background: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); font-weight: 600; }
.sqlite-tables button.active small { color: var(--vp-c-brand-1); }

.sqlite-data { display: flex; min-width: 0; flex-direction: column; }
.sqlite-toolbar { min-height: 66px; padding: 10px 18px; border-bottom: 1px solid var(--sqlite-line-soft); }
.sqlite-table-title { display: flex; min-width: 0; flex-direction: column; }
.sqlite-table-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sqlite-table-title span { color: var(--vp-c-text-3); font-size: 0.78rem; }

.sqlite-search {
  display: flex;
  width: min(260px, 45%);
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--sqlite-line);
  border-radius: 999px;
  background: var(--sqlite-surface);
  color: var(--vp-c-text-3);
  align-items: center;
  gap: 7px;
  transition: border-color 0.15s, background-color 0.15s;
}

.sqlite-search:focus-within { border-color: var(--vp-c-brand-1); background: var(--vp-c-bg); }
.sqlite-search input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--vp-c-text-1); font: inherit; font-size: 0.82rem; }

.sqlite-table-scroll { overflow: auto; min-height: 305px; flex: 1; }
.sqlite-result-table { width: max-content; min-width: 100%; margin: 0; border-collapse: collapse; font-size: 0.79rem; }
.sqlite-result-table th,
.sqlite-result-table td { max-width: 300px; padding: 11px 16px; border: 0; border-bottom: 1px solid var(--sqlite-line-soft); text-align: left; white-space: nowrap; }
.sqlite-result-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: color-mix(in srgb, var(--vp-c-bg) 92%, transparent);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--sqlite-line);
  color: var(--vp-c-text-2);
  font-weight: 600;
}
.sqlite-result-table th span,
.sqlite-result-table th small { display: block; }
.sqlite-result-table th small { margin-top: 2px; color: var(--vp-c-text-3); font-size: 0.64rem; font-weight: 500; }
.sqlite-result-table td > span { display: block; overflow: hidden; text-overflow: ellipsis; }
.sqlite-result-table tbody tr:hover { background: var(--sqlite-line-soft); }
.sqlite-cell-editable { margin: -5px -7px; padding: 5px 7px; border-radius: 6px; cursor: text; }
.sqlite-cell-editable:hover,
.sqlite-cell-editable:focus-visible { background: var(--vp-c-brand-soft); outline: 1px solid var(--vp-c-brand-1); }
.sqlite-cell-editor { display: flex; min-width: 280px; align-items: stretch; flex-direction: column; gap: 5px; }
.sqlite-cell-editor-controls { display: flex; align-items: center; gap: 4px; }
.sqlite-cell-editor input { width: 150px; min-width: 80px; padding: 5px 7px; border: 1px solid var(--vp-c-brand-1); border-radius: 6px; outline: 0; background: var(--vp-c-bg); color: var(--vp-c-text-1); font: inherit; flex: 1; }
.sqlite-cell-editor input[aria-invalid="true"] { border-color: var(--vp-c-danger-1); }
.sqlite-cell-editor button { display: inline-flex; padding: 5px 7px; border: 1px solid var(--sqlite-line); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-2); font: inherit; font-size: 0.7rem; align-items: center; justify-content: center; cursor: pointer; }
.sqlite-cell-editor button:hover { border-color: var(--vp-c-brand-1); color: var(--vp-c-brand-1); }
.sqlite-cell-editor button:disabled { cursor: not-allowed; opacity: 0.4; }
.sqlite-cell-editor-confirm { border-color: var(--vp-c-brand-1) !important; background: var(--vp-c-brand-soft) !important; color: var(--vp-c-brand-1) !important; }
.sqlite-cell-edit-error { max-width: 360px; color: var(--vp-c-danger-1); font-size: 0.68rem; line-height: 1.35; white-space: normal; }
.sqlite-cell-edit-note { max-width: 360px; color: var(--vp-c-text-3); font-size: 0.68rem; line-height: 1.35; white-space: normal; }
.sqlite-cell-null { color: var(--vp-c-text-3); }
.sqlite-cell-blob {
  display: inline-flex;
  padding: 3px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: var(--sqlite-line-soft);
  color: var(--vp-c-text-2);
  font-family: var(--vp-font-family-mono);
  font-size: 0.72rem;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}
.sqlite-cell-blob:hover,
.sqlite-cell-blob:focus-visible { border-color: var(--vp-c-brand-1); background: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); outline: none; }
.sqlite-cell-date { font-variant-numeric: tabular-nums; }
.sqlite-empty { display: grid; min-height: 305px; color: var(--vp-c-text-3); font-size: 0.86rem; place-items: center; }

.sqlite-pagination { min-height: 48px; padding: 8px 18px; border-bottom: 1px solid var(--sqlite-line-soft); background: var(--sqlite-surface); color: var(--vp-c-text-3); font-size: 0.78rem; }
.sqlite-pagination > div { display: flex; align-items: center; gap: 10px; }
.sqlite-pagination button { display: grid; width: 28px; height: 28px; padding: 0; place-items: center; }
.sqlite-pagination button:hover:not(:disabled) { background: var(--vp-c-bg-mute); color: var(--vp-c-brand-1); }
.sqlite-pagination button:disabled,
.sqlite-primary-button:disabled { cursor: not-allowed; opacity: 0.4; }

/* Teleported to <body>, so it can only rely on VitePress' own variables —
   the --sqlite-* ones live on .sqlite-editor and are out of reach here. */
.sqlite-blob-overlay {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  padding: 24px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(2px);
  place-items: center;
}

.sqlite-blob-dialog {
  display: flex;
  width: min(860px, 100%);
  max-height: min(680px, calc(100vh - 48px));
  border: 1px solid color-mix(in srgb, var(--vp-c-text-1) 10%, transparent);
  border-radius: 18px;
  outline: none;
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
  flex-direction: column;
  overflow: hidden;
}

.sqlite-blob-header {
  display: flex;
  min-height: 62px;
  padding: 10px 18px;
  border-bottom: 1px solid color-mix(in srgb, var(--vp-c-text-1) 6%, transparent);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.sqlite-blob-header > div:first-child { display: flex; min-width: 0; flex-direction: column; }
.sqlite-blob-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--vp-font-family-mono); font-size: 0.86rem; }
.sqlite-blob-header span { color: var(--vp-c-text-3); font-size: 0.78rem; }
.sqlite-blob-actions { display: flex; gap: 4px; }
.sqlite-blob-actions button {
  display: inline-flex;
  padding: 7px 11px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--vp-c-text-1);
  font: inherit;
  font-size: 0.8rem;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.sqlite-blob-actions button:disabled { cursor: not-allowed; opacity: 0.4; }
.sqlite-blob-actions .sqlite-download-button { color: var(--vp-c-brand-1); font-weight: 600; }
.sqlite-blob-actions .sqlite-download-button:hover { background: var(--vp-c-brand-soft); }
.sqlite-blob-actions .sqlite-ghost-button:hover:not(:disabled) { background: var(--vp-c-bg-mute); }

.sqlite-blob-modes {
  display: flex;
  padding: 10px 18px;
  border-bottom: 1px solid color-mix(in srgb, var(--vp-c-text-1) 6%, transparent);
  background: color-mix(in srgb, var(--vp-c-bg-soft) 88%, transparent);
  gap: 6px;
}

.sqlite-blob-modes button {
  padding: 5px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--vp-c-text-2);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.sqlite-blob-modes button:hover { background: var(--vp-c-bg-mute); }
.sqlite-blob-modes button.active { background: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); font-weight: 600; }

.sqlite-blob-body { overflow: auto; padding: 14px 18px; flex: 1; }
.sqlite-blob-body pre { margin: 0; padding: 0; background: transparent; font-family: var(--vp-font-family-mono); font-size: 0.74rem; line-height: 1.7; }
.sqlite-blob-hex { display: flex; width: max-content; flex-direction: column; }
.sqlite-blob-line { white-space: pre; }
.sqlite-blob-line i { margin-right: 14px; color: var(--vp-c-text-3); font-style: normal; }
.sqlite-blob-line b { margin-left: 14px; color: var(--vp-c-brand-1); font-weight: 400; }
.sqlite-blob-text { white-space: pre-wrap; word-break: break-word; }
.sqlite-blob-empty { margin: 0; padding: 28px 0; color: var(--vp-c-text-3); font-size: 0.86rem; text-align: center; }

.sqlite-blob-footer {
  padding: 10px 18px;
  border-top: 1px solid color-mix(in srgb, var(--vp-c-text-1) 6%, transparent);
  background: color-mix(in srgb, var(--vp-c-bg-soft) 88%, transparent);
  color: var(--vp-c-text-3);
  font-size: 0.75rem;
}

@media (max-width: 760px) {
  .sqlite-blob-overlay { padding: 12px; }
  .sqlite-blob-dialog { max-height: calc(100vh - 24px); }
  .sqlite-blob-header { align-items: flex-start; flex-direction: column; }
  .sqlite-blob-actions { width: 100%; flex-wrap: wrap; }
  .sqlite-blob-body pre { font-size: 0.68rem; }
}

@media (max-width: 760px) {
  .sqlite-editor {
    position: static;
    width: 100%;
    transform: none;
  }
  .sqlite-dropzone { min-height: 260px; padding: 24px 16px; }
  .sqlite-browser { display: block; }
  .sqlite-tables { display: flex; overflow-x: auto; padding: 8px; border-right: 0; border-bottom: 1px solid var(--sqlite-line-soft); gap: 5px; }
  .sqlite-tables-heading { display: none; }
  .sqlite-tables button { width: auto; min-width: max-content; gap: 12px; }
  .sqlite-toolbar { align-items: stretch; flex-direction: column; }
  .sqlite-search { width: 100%; }
  .sqlite-pagination { align-items: flex-start; flex-direction: column; }
  .sqlite-pagination > div { width: 100%; justify-content: space-between; }
  .sqlite-file-bar { align-items: flex-start; flex-direction: column; }
  .sqlite-file-actions { width: 100%; flex-wrap: wrap; }
}
</style>
