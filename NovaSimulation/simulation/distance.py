"""
This module contains distance-based simulation
structures.
"""
import json


class DistanceFile:
    """
    Class that handles distances data initially in json format.

    Provides methods to change the format of json
    to something easier to process by python.
    This class is used mainly for handling e.g. missing,
    inconsistent data.

    Args:
        input_file (str): path to distances.json file
    """

    def __init__(self, input_file):
        self.distance_file = input_file
        self.dist_list = self.json_to_numpy_array()
        self.avg_list = self.calculate_average_distances()
        self.avg_dict = self.average_dist_dict()

    def json_to_numpy_array(self):
        """
        Read json distances files and turn it into list

        Opens distances json file,
        Iterates on objects a list in form of:
        [devA, devB, distance]
        Read it as distance from device A to device B

        Returns:
            list of lists: [[devA, devB, distance], ...]
        """
        with open(self.distance_file, "r") as dist_file:
            distances_str = dist_file.read()

        distances_json = json.loads(distances_str)

        dist_list = []
        for i in distances_json:
            temp = distances_json[i]['phoneName']
            for j in distances_json[i]['distanceList']:
                dist_list.append([temp, j['phoneName'], j['distance']])

        return dist_list

    def calculate_average_distances(self):
        """
        Remove the differences between distances by using mean values

        if distances between devices: A -> B and B - > A
        is not the same, then use mean value of the two

        Returns:
            list
        """
        avg_list = []
        for i in self.dist_list:
            for j in self.dist_list:
                if i[0] + i[1] == j[1] + j[0]:
                    val_one, val_two = float(i[2]), float(j[2])
                    if val_one > 1 and val_two > 1:
                        mean_val = (val_one + val_two)/2
                    else:
                        mean_val = val_one if val_one > val_two else val_two
                    avg_list.append([i[0], i[1], mean_val])
        return avg_list

    def average_dist_dict(self):
        """
        Turn avg distances list into dictonary
        """
        avg_dict, temp_dict = {}, {}

        dist_iter = iter(self.avg_list)
        next(dist_iter)
        for i in self.avg_list:
            temp_dict[i[1]] = i[2]
            try:
                next_iter = next(dist_iter)
                if next_iter[0] != i[0]:
                    avg_dict[i[0]] = temp_dict
                    temp_dict = {}
            except StopIteration:
                avg_dict[i[0]] = temp_dict
        return avg_dict
