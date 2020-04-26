package com.samsung.sel.cqe.nova.main.structure

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class ClusterInfo(
    @Transient @Volatile var state: ClusterState,
    var clusterId: String,
    var closeNeighbourId: String = "",
    var fartherNeighbourId: String = ""
)


@Throws(JsonSyntaxException::class)
fun convertClusterInfoFromJson(message: String): ClusterInfo {
    return Gson().fromJson(message, ClusterInfo::class.java)
}

fun convertClusterInfoToJsonString(message: ClusterInfo): String {
    return Gson().toJson(message)
}
