package au.com.codeka.warworlds.server.store.base

import au.com.codeka.warworlds.common.Log
import au.com.codeka.warworlds.server.store.StoreException
import com.google.common.base.Preconditions
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Types
import java.util.*

/**
 * Base class for [StoreReader] and [StoreWriter] that handling building the query.
 */
open class StatementBuilder<T : StatementBuilder<T>>(
    dataSource: SQLiteConnectionPoolDataSource,
    protected val transaction: Transaction?) : AutoCloseable {
  protected lateinit var stmt: PreparedStatement

  protected val conn: Connection =
      transaction?.conn ?: dataSource.pooledConnection.connection

  private var sql: String? = null
  private var params: ArrayList<Any?>? = null

  /**
   * We store any errors we get building the statement and throw it when it comes time to execute.
   * In reality, it should be rare, since it would be an indication of programming error.
   */
  protected var e: SQLException? = null

  fun stmt(sql: String): T {
    this.sql = Preconditions.checkNotNull(sql)
    try {
      stmt = conn.prepareStatement(sql)
      params = ArrayList()
    } catch (e: SQLException) {
      log.error("Unexpected error preparing statement.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  fun param(index: Int, value: String?): T {
    try {
      if (value == null) {
        stmt.setNull(index + 1, Types.VARCHAR)
      } else {
        stmt.setString(index + 1, value)
      }
      saveParam(index, value)
    } catch (e: SQLException) {
      log.error("Unexpected error setting parameter.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  fun param(index: Int, value: Double?): T {
    try {
      if (value == null) {
        stmt.setNull(index + 1, Types.DOUBLE)
      } else {
        stmt.setDouble(index + 1, value)
      }
      saveParam(index, value)
    } catch (e: SQLException) {
      log.error("Unexpected error setting parameter.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  fun param(index: Int, value: Long?): T {
    try {
      if (value == null) {
        stmt.setNull(index + 1, Types.INTEGER)
      } else {
        stmt.setLong(index + 1, value)
      }
      saveParam(index, value)
    } catch (e: SQLException) {
      log.error("Unexpected error setting parameter.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  fun param(index: Int, value: Int?): T {
    try {
      if (value == null) {
        stmt.setNull(index + 1, Types.INTEGER)
      } else {
        stmt.setInt(index + 1, value)
      }
      saveParam(index, value)
    } catch (e: SQLException) {
      log.error("Unexpected error setting parameter.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  fun param(index: Int, value: ByteArray?): T {
    try {
      if (value == null) {
        stmt.setNull(index + 1, Types.BLOB)
      } else {
        stmt.setBytes(index + 1, value)
      }
      saveParam(index, value)
    } catch (e: SQLException) {
      log.error("Unexpected error setting parameter.", e)
      this.e = e
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  /**
   * Execute the current state and returns the number of rows affected, or -1 if this was a select.
   */
  open fun execute(): Int {
    if (e != null) {
      throw StoreException(e!!)
    }

    val startTime = System.nanoTime()
    return try {
      if (!stmt.execute()) {
        stmt.updateCount
      } else {
        -1
      }
    } catch (e: SQLException) {
      throw StoreException(e)
    } finally {
      val endTime = System.nanoTime()
      log.debug("%.2fms %s", (endTime - startTime) / 1000000.0, debugSql(sql, params))
    }
  }

  override fun close() {
    if (transaction != null) {
      try {
        conn.close()
      } catch (e: SQLException) {
        throw StoreException(e)
      }
    }
  }

  private fun saveParam(index: Int, value: Any?) {
    while (params!!.size <= index) {
      params!!.add(null)
    }
    params!![index] = value
  }

  companion object {
    private val log = Log("StatementBuilder")

    private fun debugSql(sql: String?, params: ArrayList<Any?>?): String {
      var sql = sql
      sql = sql!!.replace("\n", " ")
      sql = sql.replace(" +".toRegex(), " ")
      if (sql.length > 70) {
        sql = sql.substring(0, 68) + "..."
      }
      for (i in params!!.indices) {
        sql += " ; "
        sql += params[i]
      }
      return sql
    }
  }
}