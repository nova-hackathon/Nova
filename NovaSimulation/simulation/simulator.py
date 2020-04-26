"""
Main simulation module, it is responsible
for graph, node, edge creation
and simulation display
"""
from itertools import zip_longest
import logging
import math
import random
import time

import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
import mplcursors
import networkx as nx
import numpy as np

from device import Device, PatientDevice
from patient import Patient
from distance import DistanceFile
from geometry import get_intersection_of_two_circles, centroid
from graph import create_nan_edges, create_socket_edges, scan_in_range, distance_map_plot
from plot import draw_distance_map


LOG = logging.getLogger(__name__)
plt.style.use('ggplot')


def generate_random_grid(width, length, device_count):
    """
    Generates 'random.txt' file with grid with
    devices set on random locations.

    Coordinates are generated randomly, so if you specify
    device_count as N, it will probaly be less than N,
    becuase duplicate coordinates might get generated.

    Args:
        width (int): width of the grid
        length (int): length of the grid
        device_count (int): desired amount of devices on grid
    """
    temp_matrix = np.zeros((width, length), dtype='i')

    for i in range(0, device_count):
        x_pos, y_pos = random.randint(0, width-1), random.randint(0, length-1)
        temp_matrix[x_pos][y_pos] = i + 1

    np.savetxt('random.txt', temp_matrix, fmt="%.1i", delimiter=',')


class GraphVisualization:

    def __init__(self, graph, grid_size, *, disable_labels=False, nan_graph=None):
        self.graph = graph
        self.grid_size = grid_size
        self.pos = nx.get_node_attributes(graph, 'pos')
        self.disable_labels = bool(disable_labels)
        self.fig, self.ax = None, None
        self.nan_graph = nan_graph

    def create_plot(self):
        return plt.subplots()

    def subscribe_on_click(self):
        cursor = mplcursors.cursor()
        lookup_object_by_coords = {i.coordinates: i for i in self.graph}
        @cursor.connect("add")
        def _(sel):
            print("dong")
            coords, node_value = onlick_return_coordinates(sel, lookup_object_by_coords)
            self.on_click(coords=coords, node_value=node_value)

    def setup_plot(self):
        self.ax.invert_yaxis()
        self.ax.xaxis.tick_top()
        self.ax.yaxis.tick_left()
        self.ax.yaxis.set_ticks(np.arange(0, self.grid_size, 1))
        self.ax.xaxis.set_ticks(np.arange(0, self.grid_size, 1))
        plt.subplots_adjust(left=0.03, right=0.97, bottom=0.03, top=0.97)
        plt.margins(0.03)
        plt.grid(linestyle='--')

    def _before_show(self):
        self.fig, self.ax = self.create_plot()
        self.setup_plot()
        self.draw()
        # weird but subscribe must come after draw. otherwise, doesn't work
        self.subscribe_on_click()

    def show(self):
        self._before_show()
        plt.show()

    def animate(self, duration=10, redraws_per_second=10):
        self._before_show()
        interval = 1 / redraws_per_second
        start_time = time.time()
        for frame_no in range(duration * redraws_per_second):
            self.redraw(
                frame_no=frame_no,
                elapsed_seconds=time.time() - start_time
            )
            plt.pause(interval)

    @property
    def default_node_size(self):
        return 3000 / self.grid_size

    def draw_edges(self, filter_fun=None, **kwargs):
        if filter_fun:
            if "edgelist" in kwargs:
                raise TypeError("must not provide edgelist and filter_fun together")
            kwargs["edgelist"] = [filter_fun(*uvdata) for uvdata in self.graph.edges(data=True)]
        nx.draw_networkx_edges(self.graph, self.pos, **kwargs)

    def draw_labels(self, **kwargs):
        if self.disable_labels:
            return
        nx.draw_networkx_labels(self.graph, self.pos, **kwargs)

    def draw_nodes(self, *, filter_fun=None, **kwargs):
        if "node_size" not in kwargs:
            kwargs["node_size"] = self.default_node_size
        if filter_fun:
            if "nodelist" in kwargs:
                raise TypeError("must not provide nodelist and filter_fun together")
            kwargs["nodelist"] = list(filter(filter_fun, self.graph))
        nx.draw_networkx_nodes(self.graph, self.pos, **kwargs)

    def on_click(self, coords, node_value):
        pass

    def draw(self):
        pass

    def redraw(self, **kwargs):
        pass


class MeshViz(GraphVisualization):
    """
    Demonstrates mesh network organization, incl. masater-slave roles of nodes.
    """

    def draw(self):
        self.draw_nodes(
            filter_fun=lambda node: node.is_master,
            node_color="r"
        )
        self.draw_nodes(
            filter_fun=lambda node: not node.is_master,
            node_color="g"
        )
        self.draw_labels()
        if self.nan_graph is not None:
            nx.draw_networkx_edges(self.nan_graph, self.pos, edge_color='y', alpha=0.7)
        self.draw_edges(
            edge_color='b',
            alpha=0.7
        )

class MeasurementsViz(MeshViz):
    """
    Demonstrates mesh usage for monitoring health parameters of patients.
    """
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.mutator = Mutator(self.graph)

    def on_click(self, coords, node_value):
        show_saturation_history(coords, node_value)

    def __draw_nodes(self):
        self.mutator.tick()
        self.mutator.measure('spo2')
        self.draw_nodes(
            node_color=[
                (0, (node.get_last_measurement('spo2').value - 75) / 25, 0) for node in self.graph
            ]
        )

    def draw(self):
        self.__draw_nodes()
        self.draw_labels()
        if self.nan_graph is not None:
            nx.draw_networkx_edges(self.nan_graph, self.pos, edge_color='y', alpha=0.7)
        self.draw_edges(
            edge_color='b',
            alpha=0.7
        )

    def redraw(self, **kwargs):
        self.__draw_nodes()

def path_onclick_wrapper(node_collection, node_value, graph):
    node_collection.append(node_value)
    if len(node_collection) > 1:
        # draw legend
        red_patch = mpatches.Patch(color='crimson', label='Main path')
        magenta_patch = mpatches.Patch(color='magenta', label='Backup path')
        plt.legend(handles=[red_patch, magenta_patch], loc='upper right')

        main_path, backup_path = calculate_path_between(graph, 
                                                        node_collection[-1], 
                                                        node_collection[-2])
        main_graph = create_path_edges(main_path)
        backup_graph = create_path_edges(backup_path)

        draw_path(backup_graph, 'magenta')
        draw_path(main_graph, 'crimson')
        node_collection.clear()


def show_saturation_history(coords, node_value):
    
    data = [99, 100, 100, 99, 98, 97, 97, 96, 95, 94]
    x_ticks = [i for i in range(1, 11)]

    fig, ax = plt.subplots()

    ax.axhline(90, ls='--', color='red')
    ax.axhline(95, ls='--', color='yellow')

    ax.bar(x_ticks, data, color='royalblue')
    ax.yaxis.set_ticks([i for i in range(0, 110, 10)])
    ax.xaxis.set_ticks(x_ticks)

    plt.show()


def onlick_return_coordinates(sel, lookup_object_by_coords):
    sel_x = int(round(sel.target[0]))
    sel_y = int(round(sel.target[1]))
    node_value = lookup_object_by_coords[(sel_x, sel_y)]

    sel.annotation.set_text(f"{node_value} \n ({sel_x}, {sel_y})")
    sel.annotation.set_text(f"({sel_x}, {sel_y})")
    sel.annotation.get_bbox_patch().set(fc="white")
    sel.annotation.arrow_patch.set(arrowstyle="simple", fc="white", alpha=.5)

    return(sel_x, sel_y), node_value


def calculate_path_between(graph, node_1, node_2):
    main_path = nx.shortest_path(graph, node_1, node_2)
    graph.remove_nodes_from(main_path[1:-1])
    backup_path = nx.shortest_path(graph, node_1, node_2)
    
    return main_path, backup_path


def create_path_edges(path):
    """
    make connections between nodes
    """
    path_graph = nx.Graph()
    for i, num in enumerate(path):
        path_graph.add_node(num, pos=num.coordinates, value=num.value)
        if i < len(path) - 1:
            path_graph.add_edge(path[i], path[i+1])

    return path_graph


def draw_path(graph, color):
    """
    draw path on given graph object
    """
    pos = nx.get_node_attributes(graph, 'pos')
    nx.draw_networkx_edges(graph, pos, graph.edges, edge_color=color, alpha=1, width=2)


class Mutator:

    def __init__(self, devices):
        self.devices = devices
        self.timestamp = 0
        self.__measured = set()
    
    @property
    def patient_devices(self):
        for dev in self.devices:
            if isinstance(dev, PatientDevice):
                yield dev
    
    def tick(self):
        """
        Moves time forward and mutates state of all patients.
        """
        self.timestamp += 1
        self.__measured.clear()
        for dev in self.patient_devices:
            dev.patient.tick()
    
    def measure(self, param):
        """
        Measures *param* on all patient devices.
        """
        if param in self.__measured:
            return # already measured
        for dev in self.patient_devices:
            dev.measure(self.timestamp, param)


def simulation_plot(variant, input_path):
    with open(input_path, encoding='utf8') as f:
        matrix = np.loadtxt(f, dtype='i', delimiter=',')

    radius = 5
    device_limit = 5
    patient_id = 0
    
    devices = []
    start = time.time()
    for i, num in enumerate(matrix.transpose()):
        for j, num2 in enumerate(num):
            if num2 != 0:
                devices.append(PatientDevice((i, j), num2, patient=Patient(patient_id)))
                patient_id += 1
    LOG.error(f"creating device objects: {time.time() - start :.2f} s")

    G = nx.Graph()
    for i in devices:
        G.add_node(i, pos=i.coordinates, value=i.value)

    matrix_size = len(matrix)
    nan_graph = G.copy()

    scan_in_range(G, radius)
    create_socket_edges(G, device_limit)
    create_nan_edges(nan_graph)
    if variant == "mesh":
        MeshViz(
            G,
            disable_labels=True,
            grid_size=matrix_size,
            nan_graph=nan_graph
        ).show()
    else:
        MeasurementsViz(
            G,
            disable_labels=True,
            grid_size=matrix_size,
            nan_graph=nan_graph
        ).animate(redraws_per_second=5)


if __name__ == "__main__":
    import sys
    USAGE = (
        "usage: python simulator.py {gengrid|distplot|help}\n"
        "   or: python simulator.py simplot mesh [INPUT_FILE]\n"
        "   or: python simulator.py simplot health [INPUT_FILE]"
    )
    if len(sys.argv) <= 1 or sys.argv[0] in ("-h", "help", "--help"):
        print(USAGE)
        sys.exit()
    cmd = sys.argv[1]
    if cmd == "simplot":
        if len(sys.argv) == 2:
            sys.exit(f"missing simplot variant (e.g. mesh)\n{USAGE}")
        variant = sys.argv[2]
        if variant not in ("mesh", "health"):
            sys.exit(f"unknown variant: {variant}\n{USAGE}")
        if len(sys.argv) > 3:
            input_path = sys.argv[3]
        else:
            input_path = "input/input.txt"
        simulation_plot(variant, input_path)
    elif cmd == "gengrid":
        generate_random_grid(100, 100, 2000)
    elif cmd == "distplot":
        distance_graph = distance_map_plot()
        # draw_distance_map(distance_graph)
    else:
        sys.exit(f"unknown command: {cmd}\n{USAGE}")
