package org.delcom.repositories

import org.delcom.dao.TodoDAO
import org.delcom.entities.Todo
import org.delcom.helpers.suspendTransaction
import org.delcom.helpers.todoDAOToModel
import org.delcom.tables.TodoTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import java.util.*

class TodoRepository : ITodoRepository {
    override suspend fun getAll(
        userId: String,
        search: String,
        isDone: Boolean?,
        urgency: String?,
        page: Int,
        perPage: Int
    ): List<Todo> = suspendTransaction {
        val userUuid = UUID.fromString(userId)
        val offset = ((page - 1) * perPage).toLong()
        
        TodoDAO.find {
            var op: Op<Boolean> = TodoTable.userId eq userUuid
            
            if (search.isNotBlank()) {
                op = op and (TodoTable.title.lowerCase() like "%${search.lowercase()}%")
            }

            if (isDone != null) {
                op = op and (TodoTable.isDone eq isDone)
            }

            if (!urgency.isNullOrBlank()) {
                op = op and (TodoTable.urgency eq urgency)
            }
            op
        }.orderBy(
            if (search.isNotBlank()) TodoTable.title to SortOrder.ASC
            else TodoTable.createdAt to SortOrder.DESC
        )
        .limit(perPage)
        .offset(offset)
        .map(::todoDAOToModel)
    }

    override suspend fun getById(todoId: String): Todo? = suspendTransaction {
        TodoDAO
            .find {
                TodoTable.id eq UUID.fromString(todoId)
            }
            .limit(1)
            .map(::todoDAOToModel)
            .firstOrNull()
    }

    override suspend fun create(todo: Todo): String = suspendTransaction {
        val todoDAO = TodoDAO.new {
            userId = UUID.fromString(todo.userId)
            title = todo.title
            description = todo.description
            cover = todo.cover
            isDone = todo.isDone
            urgency = todo.urgency
            createdAt = todo.createdAt
            updatedAt = todo.updatedAt
        }

        todoDAO.id.value.toString()
    }

    override suspend fun update(userId: String, todoId: String, newTodo: Todo): Boolean = suspendTransaction {
        val todoDAO = TodoDAO
            .find {
                (TodoTable.id eq UUID.fromString(todoId)) and
                        (TodoTable.userId eq UUID.fromString(userId))
            }
            .limit(1)
            .firstOrNull()

        if (todoDAO != null) {
            todoDAO.title = newTodo.title
            todoDAO.description = newTodo.description
            todoDAO.cover = newTodo.cover
            todoDAO.isDone = newTodo.isDone
            todoDAO.urgency = newTodo.urgency
            todoDAO.updatedAt = newTodo.updatedAt
            true
        } else {
            false
        }
    }

    override suspend fun delete(userId: String, todoId: String): Boolean = suspendTransaction {
        val rowsDeleted = TodoTable.deleteWhere {
            (TodoTable.id eq UUID.fromString(todoId)) and (TodoTable.userId eq UUID.fromString(userId))
        }
        rowsDeleted >= 1
    }

    override suspend fun getStats(userId: String): Map<String, Long> = suspendTransaction {
        val userUuid = UUID.fromString(userId)
        
        val total = TodoDAO.find { TodoTable.userId eq userUuid }.count()
        val finished = TodoDAO.find { (TodoTable.userId eq userUuid) and (TodoTable.isDone eq true) }.count()
        val unfinished = TodoDAO.find { (TodoTable.userId eq userUuid) and (TodoTable.isDone eq false) }.count()

        mapOf(
            "total" to total,
            "finished" to finished,
            "unfinished" to unfinished
        )
    }

}
