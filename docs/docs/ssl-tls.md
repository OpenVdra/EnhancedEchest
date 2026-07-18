# SSL / TLS

The `ssl` option controls transport encryption for a remote MySQL, MariaDB, or PostgreSQL database. It has no effect on SQLite. Changing it requires a full server restart.

| Value | Behaviour |
| --- | --- |
| `disable` | No encryption (default). |
| `require` | Encrypts the connection but does **not** verify the server certificate or hostname. Stops passive snooping, but not an active man-in-the-middle. |
| `verify-full` | Encrypts **and** verifies the certificate chain and hostname. The only mode that defends against a man-in-the-middle. |

`verify-full` requires the database server's certificate authority to be trusted by the JVM running your Minecraft server (its truststore). If the certificate is self-signed or issued by a private CA, import it into the JVM truststore first, otherwise the connection will fail at startup.
