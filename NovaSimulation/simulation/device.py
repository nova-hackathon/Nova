import collections
import functools
import logging
import random
import time

import networkx as nx

import patient

LOG = logging.getLogger(__name__)

PHONE_STATUSES = {
            'MASTER': 'master',
            'CLIENT': 'client',
            'CLIENT_SERVER': 'client_server',
            'CLIENT_OUT': 'client_out',
            'UNDECIDED': 'undecided',
            'CLIENT_SERVER_AWAITS_RECONNECT': 'client_server_awaits_reconnect',
            'CLOSING': 'closing',
        }
MAX_CLIENT_COUNT = 3
MAX_MASTER_COUNT = 1


Measurement = collections.namedtuple('Measurement', 'timestamp value')


class Device:
    """
    A class describing single device

    Args:
        coordinates (tuple): (n, n) unique 
        value (int): placeholder value until on_since is fully implemented
    """
    def __init__(self, coordinates, value):
        self.coordinates = coordinates
        self.value = value
        self.is_master = False
        self.devices_in_range = {}
        self.connections = []

    def __str__(self):
        return str(self.value)

    def __repr__(self):
        return str(self.value)

    def __lt__(self, other):
        if isinstance(other, Device):
            if self.value < other.value:
                return True
            else:
                return False

    def add_device_in_range(self, node, distance):
        self.devices_in_range[node] = distance

    def add_connections(self, node):
        self.connections.append(node)


class PatientDevice(Device):
    """
    A device assigned to a patient.

    Such a device is able to record a modest history of measurements of a patient's state.
    """

    # Simulates disconnected or otherwise misbehaving sensors.
    # Various pieces of equipment may produce various values in such scenarios,
    # e.g. "special" values (-1, 0, etc.) or "unlikely" values.
    FAULTY = {
        'hr': (-1, -1),
        'spo2': (0, 5)
    }

    def __init__(self, coordinates, value, *,
            battery_level=100,
            patient,
            buffer_size=10):
        super().__init__(coordinates, value)
        self.battery_level = battery_level
        self.patient = patient
        self.__buffer = collections.defaultdict(functools.partial(collections.deque, maxlen=buffer_size))
        self.faulty = set() # set of params for which sensors are currently faulty

    def measure(self, timestamp, param):
        """
        Records a *param* measurement and returns it.
        """
        if param in self.faulty:
            value = random.randint(*self.FAULTY[param])
        else:
            value = self.patient.measure(param)
        self.__buffer[param].append(Measurement(timestamp, value))
        return value

    def get_last_measurement(self, param):
        """
        Gets *param* last measurment.
        """
        return self.__buffer[param][-1]
    
    def get_measurements(self, param):
        """
        Gets *param* measurement history.
        """
        return tuple(self.__buffer[param])
    
    def pop_measurements(self, param):
        """
        Clears *param* measurement history and returns it.
        """
        return tuple(self.__buffer.pop(param, ()))


class ComplexDevice:
    """
    A complex version of Device class, that
    more closely resembles the actual, real device.

    Args:
        phone_id (str): id of a device
        phone_name (str): unique device name
        mac_address (str)
    """

    def __init__(self, phone_id, phone_name, mac_address):
        self.phone_id = phone_id
        self.phone_name = phone_name
        self.mac_address = mac_address
        self.master_rank = None
        self.is_master = False
        self.accepts_connection = False
        self.socket_client_count = 0
        self.status = 'UNDECIDED'

    @property
    def socket_client_count(self):
        return self._socket_client_count

    @socket_client_count.setter
    def socket_client_count(self, value):
        if value > MAX_CLIENT_COUNT:
            raise ValueError("client count exceeded, maximum is: %s" % MAX_CLIENT_COUNT)
        else:
            self._socket_client_count = value

    @property
    def status(self):
        return self._status

    @status.setter
    def status(self, value):
        if value not in PHONE_STATUSES.keys():
            raise KeyError("Wrong phone status")
        else:
            self._status = value

    def update_master_rank(self):
        self.master_rank = random.randrange(21474836)

    def select_master(self, other):
        if isinstance(other, ComplexDevice):
            if self.master_rank > other.master_rank:
                return True
            else:
                return False


class Cluster:
    """
    Object respresenting multiple devices
    connected together into a cluster
    """
    def __init__(self, phone_id):
        self.cluster_id = phone_id
        self.close_neighbour_id = None
        self.farther_neighbour_id = None
        self.is_cluster_connection_created = True
        self.phone_status_for_cluster_connection = PHONE_STATUSES['CLIENT_SERVER']
        self.cluster_id_to_reconnect = None
