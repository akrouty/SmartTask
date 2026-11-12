package com.example.smart_task.network
import com.example.smart_task.network.dto.ChangePasswordRequest
import com.example.smart_task.network.dto.CreateProjectRequest
import com.example.smart_task.network.dto.CreateTaskRequest
import com.example.smart_task.network.dto.InviteRequest
import com.example.smart_task.network.dto.InviteResponseDto
import com.example.smart_task.network.dto.ProjectMembersResponse
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.network.dto.TaskApiDto
import com.example.smart_task.network.dto.TasksResponse
import com.example.smart_task.network.dto.UpdateMeRequest
import com.example.smart_task.network.dto.UpdateProjectRequest
import com.example.smart_task.network.dto.UpdateTaskRequest
import com.example.smart_task.network.dto.UserMeDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {

    @POST("/api/auth/login")
    fun login(@Body body: LoginRequest): Call<LoginResponse>
    @POST("api/auth/register")
    fun register(@Body body: `RegisterRequest`): Call<LoginResponse>


    @GET("api/Projects")
    fun getProjects(@Header("Authorization") token: String): Call<List<ProjectResponseDto>>

    @POST("api/projects")
    fun createProject(
        @Header("Authorization") token: String,
        @Body body: CreateProjectRequest
    ): Call<ProjectResponseDto>
    @PUT("api/Projects/{id}")
    fun updateProject(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateProjectRequest
    ): Call<Unit>

    @DELETE("api/Projects/{id}")
    fun deleteProject(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Call<Unit>

    @POST("api/Projects/{projectId}/invites")
    fun inviteMember(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: String,
        @Body body: InviteRequest
    ): Call<InviteResponseDto>

    @GET("api/Tasks")
    fun getTasks(
        @Header("Authorization") token: String,
        @Query("projectId") projectId: String? = null
    ): Call<TasksResponse>


    @PUT("api/Tasks/{id}")
    fun updateTask(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateTaskRequest
    ): Call<TaskApiDto>

    @POST("api/Tasks")
    fun createTask(
        @Header("Authorization") token: String,
        @Body body: CreateTaskRequest
    ): Call<TaskApiDto>

    @DELETE("api/Tasks/{id}")
    fun deleteTask(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Call<Unit>
    @GET("/api/users/me")
    fun getMe(@Header("Authorization") auth: String): Call<UserMeDto>

    @PUT("/api/users/me")
    fun updateMe(
        @Header("Authorization") auth: String,
        @Body body: UpdateMeRequest
    ): Call<UserMeDto>

    @PUT("/api/users/me/password")
    fun changePassword(
        @Header("Authorization") auth: String,
        @Body body: ChangePasswordRequest
    ): Call<Unit>
    @GET("api/Projects/{projectId}/members")
    fun getProjectMembers(
        @Header("Authorization") auth: String,
        @Path("projectId") projectId: String
    ): Call<ProjectMembersResponse>


}
