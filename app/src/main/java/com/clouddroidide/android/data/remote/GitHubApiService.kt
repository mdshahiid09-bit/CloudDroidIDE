package com.clouddroidide.android.data.remote

import com.clouddroidide.android.data.remote.model.ArtifactsResponse
import com.clouddroidide.android.data.remote.model.CreateRepoRequest
import com.clouddroidide.android.data.remote.model.DeviceCodeResponse
import com.clouddroidide.android.data.remote.model.GitHubRelease
import com.clouddroidide.android.data.remote.model.GitHubRepo
import com.clouddroidide.android.data.remote.model.GitHubUser
import com.clouddroidide.android.data.remote.model.OAuthTokenResponse
import com.clouddroidide.android.data.remote.model.PutSecretRequest
import com.clouddroidide.android.data.remote.model.RepoPublicKey
import com.clouddroidide.android.data.remote.model.WorkflowDispatchRequest
import com.clouddroidide.android.data.remote.model.WorkflowJobsResponse
import com.clouddroidide.android.data.remote.model.WorkflowRunsResponse
import com.clouddroidide.android.data.remote.model.WorkflowsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {
    @GET("user")
    suspend fun getAuthenticatedUser(): GitHubUser

    @GET("user/repos")
    suspend fun listUserRepos(
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 50,
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member"
    ): List<GitHubRepo>

    @POST("user/repos")
    suspend fun createRepo(@Body request: CreateRepoRequest): GitHubRepo

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>

    @GET("repos/{owner}/{repo}/actions/secrets/public-key")
    suspend fun getActionsPublicKey(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): RepoPublicKey

    @PUT("repos/{owner}/{repo}/actions/secrets/{secret_name}")
    suspend fun putActionsSecret(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("secret_name") secretName: String,
        @Body request: PutSecretRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun listWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body request: WorkflowDispatchRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun listRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowJobsResponse

    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun downloadJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<ResponseBody>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponse
}

interface GitHubOAuthService {
    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = "repo workflow read:user"
    ): DeviceCodeResponse

    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): OAuthTokenResponse
}
