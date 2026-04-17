package org.delcom

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.* // Tambahkan import ini untuk staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.delcom.data.AppException
import org.delcom.data.ErrorResponse
import org.delcom.helpers.JWTConstants
import org.delcom.helpers.parseMessageToMap
import org.delcom.services.TodoService
import org.delcom.services.AuthService
import org.delcom.services.UserService
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val todoService: TodoService by inject()
    val authService: AuthService by inject()
    val userService: UserService by inject()

    install(StatusPages) {
        // Tangkap AppException
        exception<AppException> { call, cause ->
            val dataMap: Map<String, List<String>> = parseMessageToMap(cause.message)

            call.respond(
                status = HttpStatusCode.fromValue(cause.code),
                message = ErrorResponse(
                    status = "fail",
                    message = if (dataMap.isEmpty()) cause.message else "Data yang dikirimkan tidak valid!",
                    data = if (dataMap.isEmpty()) null else dataMap.toString()
                )
            )
        }

        // Tangkap semua Throwable lainnya
        exception<Throwable> { call, cause ->
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(
                    status = "error",
                    message = cause.message ?: "Unknown error",
                    data = ""
                )
            )
        }
    }

    routing {
        // --- 1. Akses File Statis ---
        // Baris ini sangat penting agar Flutter bisa mengakses file di folder 'uploads'
        staticFiles("/uploads", File("uploads"))

        get("/") {
            call.respondText("API gagal berjalan. Tapi berlari")
        }

        // --- 2. Route Auth ---
        route("/auth") {
            post("/login") {
                authService.postLogin(call)
            }
            post("/register") {
                authService.postRegister(call)
            }
            post("/refresh-token") {
                authService.postRefreshToken(call)
            }
            post("/logout") {
                authService.postLogout(call)
            }
        }

        // --- 3. Route Terproteksi (JWT) ---
        authenticate(JWTConstants.NAME) {
            // Route User
            route("/users") {
                get("/me") {
                    userService.getMe(call)
                }
                put("/me") {
                    userService.putMe(call)
                }
                put("/me/password") {
                    userService.putMyPassword(call)
                }
                put("/me/photo") {
                    userService.putMyPhoto(call)
                }
            }

            // Route Todos
            route("/todos") {
                get {
                    todoService.getAll(call)
                }
                get("/stats") {
                    todoService.getStats(call)
                }
                post {
                    todoService.post(call)
                }
                get("/{id}") {
                    todoService.getById(call)
                }
                put("/{id}") {
                    todoService.put(call)
                }
                put("/{id}/cover") {
                    todoService.putCover(call)
                }
                delete("/{id}") {
                    todoService.delete(call)
                }
            }
        }

        // --- 4. Route Image Service (Jika ingin lewat logic service) ---
        route("/images") {
            get("users/{id}") {
                userService.getPhoto(call)
            }

            get("todos/{id}") {
                todoService.getCover(call)
            }
        }
    }
}