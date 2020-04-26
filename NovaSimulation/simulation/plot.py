"""
This module displays graph objects
"""
from itertools import zip_longest

import matplotlib.pyplot as plt
import networkx as nx
import numpy as np


def draw_distance_map(graph):
    """
    Displays distance map plots in two
    versions: mirrored and unmirrored

    Args:
        graph (:obj: networkx.Graph): collection of nodes
    """
    pos = nx.get_node_attributes(graph, 'pos')

    ax1 = plt.subplot(211)
    ax1.set_title('Not mirrored')
    ax1.xaxis.tick_top()
    ax1.yaxis.tick_left()
    ax1.yaxis.set_ticks(np.arange(-2000, 17000, 2000))
    ax1.xaxis.set_ticks(np.arange(-2000, 16000, 2000))
    nx.draw_networkx_nodes(graph, pos, node_color='g')
    nx.draw_networkx_labels(graph, pos, fontsize=10)
    plt.margins(0.1)

    ax2 = plt.subplot(212)
    ax2.set_title('Mirrored')
    ax2.invert_yaxis()
    ax2.xaxis.tick_top()
    ax2.yaxis.tick_left()
    ax2.yaxis.set_ticks(np.arange(-2000, 17000, 2000))
    ax2.xaxis.set_ticks(np.arange(-2000, 16000, 2000))
    nx.draw_networkx_nodes(graph, pos, node_color='r')
    nx.draw_networkx_labels(graph, pos, fontsize=10)
    plt.margins(0.1)

    plt.subplots_adjust(left=0.03, right=0.97, bottom=0.03, top=0.92)

    plt.show()