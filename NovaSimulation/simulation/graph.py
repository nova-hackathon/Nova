"""
This module contains function related to creation
and modification of graph objects
"""
from itertools import zip_longest
import logging
import math
import time

from device import Device
from distance import DistanceFile
from geometry import distance_simulation_loop

import networkx as nx


LOG = logging.getLogger(__name__)


def scan_in_range(graph, radius):
    """
    Iterates twice over all devices,
    checks if one is in radius of another
    if yes: saves it in object state

    Args:
        graph (:obj:) - networkx graph
        radius (int) - range of devices
    """
    start = time.time()
    for dev_a in graph:
        for dev_b in graph:
            distance = math.dist(dev_a.coordinates, dev_b.coordinates)
            if dev_a.coordinates != dev_b.coordinates and distance <= radius:
                dev_a.add_device_in_range(dev_b, distance)
    LOG.error("scan_in_range: %.2f s", time.time() - start)


def create_socket_edges(graph, device_limit):
    """
    Add socket edges to graph, algorithm
    prioritizes picking slaves by shortest
    distance to master device

    Args:
        graph (:obj:) - networkx graph
        device_limit (int) - limits how many slaves
            should one master have
    """
    start = time.time()
    g_list = set(graph)

    while g_list:
        lowest = min(g_list)
        lowest.is_master = True

        neighbours = lowest.devices_in_range
        temp_list = [node for node in sorted(neighbours, key=neighbours.get) if node in g_list]

        for device in temp_list[:device_limit]:
            graph.add_edge(lowest, device)
            lowest.add_connections(device)
            g_list.remove(device)
        g_list.remove(lowest)
    LOG.error("create_socket_edges: %.2f s", time.time() - start)


def create_nan_edges(graph):
    """
    Adds edges for NAN simulation

    NAN - near-me area network, it is different
    from socket connection by not having
    the device limit

    Args:
        graph (:obj:) - networkx graph, if both
            nan and sockets are used, it has to be
            copy of the same graph
    """
    for node in graph.nodes:
        graph.add_edges_from(zip_longest([node], node.devices_in_range, fillvalue=node))
        node.connections += node.devices_in_range


def distance_map_plot():
    result = distance_simulation_loop(DistanceFile('./input/distances.json').avg_dict)
    G = nx.Graph()

    devices = []
    for i in result:
        devices.append(Device(i[1], i[0]))
    for i in devices:
        G.add_node(i, pos=i.coordinates, value=i.value)

    return G