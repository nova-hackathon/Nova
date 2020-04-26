package map

import com.samsung.sel.cqe.nova.main.utils.DistanceInfo

data class DevicePosition(val device: DistanceInfo, val xCoordinate: Int, val yCoordinate: Int) {
    constructor(device: DistanceInfo, coordinates: Pair<Int, Int>) : this(
        device,
        coordinates.first,
        coordinates.second
    )
}