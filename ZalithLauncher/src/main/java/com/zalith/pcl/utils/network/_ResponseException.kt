/*
 * Pixelforge Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.utils.network

import com.zalith.pcl.R
import com.zalith.pcl.ui.AndroidStringText
import com.zalith.pcl.ui.androidText
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

fun ResponseException.toLocal(): AndroidStringText {
    val statusCode = response.status
    val codeString = statusCode.value.toString()
    val textRes = when (statusCode) {
        HttpStatusCode.BadRequest -> R.string.error_bad_request
        HttpStatusCode.Unauthorized -> R.string.error_unauthorized
        HttpStatusCode.Forbidden -> R.string.error_forbidden
        HttpStatusCode.NotFound -> R.string.error_notfound
        HttpStatusCode.NotAcceptable -> R.string.error_not_acceptable
        HttpStatusCode.RequestTimeout -> R.string.error_request_timeout
        HttpStatusCode.Conflict -> R.string.error_conflict
        HttpStatusCode.Gone -> R.string.error_gone
        HttpStatusCode.TooManyRequests -> R.string.error_too_many_requests
        HttpStatusCode.InternalServerError -> R.string.error_internal_server_error
        HttpStatusCode.BadGateway -> R.string.error_bad_gateway
        HttpStatusCode.ServiceUnavailable -> R.string.error_service_unavailable
        HttpStatusCode.GatewayTimeout -> R.string.error_gateway_timeout
        else -> return androidText(
            "($codeString) ${statusCode.description}"
        )
    }
    return androidText(textRes, codeString)
}

