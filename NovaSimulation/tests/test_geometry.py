from simulation.geometry import centroid
from simulation.geometry import get_intersection_of_two_circles
from simulation.geometry import observational_err_handler

import pytest

centroid_data = [
    ((0, 0), (10, 10), (-5, -5), (1.667 , 1.667)),
    ((1000, 2000), (17432.98, 8004.54), (10182.186, 544.254), (9538.389, 3516.265)),
]
two_circles_data = [
    ((0, 0), 10, (-20, 0), 10, (-10, 0)),
    ((0, 0), 10, (-30, 0), 10, (-15, 0)),
]
error_handler_data = [
    (10, 5, 5, (5, 5)),
    (0, 54, 54, None),
    (20000, 3000, 200, None)
]


class TestGeometry:

    @pytest.mark.parametrize('pos_1, pos_2, pos_3, expected', centroid_data)
    def test_centroid(self, pos_1, pos_2, pos_3, expected):
        result = centroid(pos_1, pos_2, pos_3)
        rounded_1 = round(result[0], 3)
        rounded_2 = round(result[1], 3)
        assert (rounded_1, rounded_2) == expected

    @pytest.mark.parametrize('dev_1, r1, dev_2, r2, expected', two_circles_data)
    def test_get_intersection_of_two_circles(self, dev_1, r1, dev_2, r2, expected):
        result = get_intersection_of_two_circles(dev_1, r1, dev_2, r2)
        assert result == expected

    @pytest.mark.parametrize('d, rn, rm, expected', error_handler_data)
    def test_observational_error_handler(self, d, rn, rm, expected):
        result = observational_err_handler(d, rn, rm)
        assert result == expected
