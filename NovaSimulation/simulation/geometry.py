"""
This module contains methematical functions, that are used
to calculate relative position of devices.
"""
import logging
import math


LOG = logging.getLogger(__name__)


# pylint: disable=invalid-name, too-many-arguments, too-many-locals
def get_intersection_of_two_circles(dev1_coords, r0, dev2_coords, r1):
    """
    Calculate position of 3rd device when only 2 are present.

    Details about math involved can be found at:
    http://paulbourke.net/geometry/circlesphere/
    under 'Intersection of two circles' section

    We discard coordinate that is below x axis,
    because it's a mirror image, which we can simulate
    in different ways.

    Args:
        dev1_coords (tuple): (x, y) coordinates of device no.0
        r0 (float): distance from device no.0
            to device no.3
        dev2_coords (tuple): (x, y) coordinates of device no.1
        r1 (float): distance from deivce no.1
            to device no.3

    Returns:
        (tuple) coordinates of device no.3
    """

    x0, y0 = dev1_coords[0], dev1_coords[1]
    x1, y1 = dev2_coords[0], dev2_coords[1]

    d = math.sqrt((x1 - x0)**2 + (y1 - y0)**2)

    r0, r1 = observational_err_handler(d, r0, r1)
    a = (r0**2 - r1**2 + d**2) / (2 * d)
    h = math.sqrt(r0**2 - a**2)

    x2 = x0 + a * (x1 - x0) / d
    y2 = y0 + a * (y1 - y0) / d

    x3 = (x2 + h * (y1 - y0) / d)
    y3 = (y2 - h * (x1 - x0) / d)

    x4 = (x2 - h * (y1 - y0) / d)
    y4 = (y2 + h * (x1 - x0) / d)

    if y3 < 0:
        return (x4, y4)
    return (x3, y3)


def observational_err_handler(d, rn, rm):
    """
    Increase or decrease radiuses if circles
    don't intersect, stop at maximum
    observational error value specified

    Args:
        d (float): distance between nodes
        rn (float): distance (radius) from known device n
            to currently looked for device
        rm (float): distance (radius) from known device m
            to currently looked for device
    
    Returns:
        rn, nm (float): updated radiuses
    """
    obs_err = 5000
    iterator = 0

    # circles are separate
    while d > rn + rm:
        if iterator <= 5000:
            rn += 1
            rm += 1
            iterator += 1
        else:
            LOG.error("Circles are separate")
            return None

    # one circle contained within the other
    while d < abs(rn - rm):
        if iterator <= obs_err:
            rn += 1
            rm -= 1
            iterator += 1
        if iterator >= obs_err:
            rn -= 1
            rm += 1
            iterator += 1
        if iterator >= 3 * obs_err:
            LOG.error("Circles are contained within each other")
            return None

    if d == 0 and rn == rm:
        LOG.error("Coincident circle, possible duplicate reading")
        return None

    return rn, rm


def centroid(pos_1, pos_2, pos_3):
    """
    Calculate centroid of three coordinates

    Details about math involved can be found at:
    https://www.mathopenref.com/coordcentroid.html

    Args:
        pos_1 (tuple): coordinates of device 1
        pos_2 (tuple): coordinates of device 2
        pos_3 (tuple): coordinates of device 3

    Returns:
        (tuple) coordinates
    """
    x1, y1 = pos_1[0], pos_1[1]
    x2, y2 = pos_2[0], pos_2[1]
    x3, y3 = pos_3[0], pos_3[1]
    x = (x1 + x2 + x3) / 3
    y = (y1 + y2 + y3) / 3
    return (x, y)


def distance_simulation_loop(distances):
    """
    Main loop of distance based simulation

    It is used to calculate coordiate of all
    devices, based on it's distances.
    Stars by locating first device on map,
    on location (0, 0). Then second device
    on location (-distanceA->B, 0).
    Then third by using geomtry circle intersection
    function. And each consecutive after third one
    by using three separate equations of two-circle
    intersections and using mean approximation
    of three coordinate estimates (centroid), which
    results in one, most probable location of a device.

    Args:
        distances (dict) - result of DistanceFile.avg_dict
    Returns:
        (list of tuples) device coordinates obtained
            by trilateration
    """
    unknown_pos = list(distances)
    known_pos = []

    start_dev = unknown_pos.pop(0)
    known_pos.append((start_dev, (0, 0)))

    second_dev = list(distances[start_dev])[0]
    second_pos = (-distances[start_dev][second_dev], 0)

    known_pos.append((second_dev, second_pos))
    unknown_pos.remove(second_dev)

    while unknown_pos:
        current_dev = unknown_pos.pop(0)
        dev_1, dev_1_pos = known_pos[-2][0], known_pos[-2][1]
        dev_2, dev_2_pos = known_pos[-1][0], known_pos[-1][1]
        dev_1_dist = distances[dev_1][current_dev]
        dev_2_dist = distances[dev_2][current_dev]

        if len(known_pos) <= 2:
            current_pos = get_intersection_of_two_circles(dev_1_pos, dev_1_dist,
                                                          dev_2_pos, dev_2_dist)
            known_pos.append((current_dev, current_pos))

        else:
            dev_3, dev_3_pos = known_pos[-3][0], known_pos[-3][1]
            dev_3_dist = distances[dev_3][current_dev]

            intersection_1 = get_intersection_of_two_circles(dev_1_pos, dev_1_dist,
                                                             dev_2_pos, dev_2_dist)
            intersection_2 = get_intersection_of_two_circles(dev_1_pos, dev_1_dist,
                                                             dev_3_pos, dev_3_dist)
            intersection_3 = get_intersection_of_two_circles(dev_2_pos, dev_2_dist,
                                                             dev_3_pos, dev_3_dist)

            current_pos = centroid(intersection_1, intersection_2, intersection_3)
            known_pos.append((current_dev, current_pos))

    return known_pos
