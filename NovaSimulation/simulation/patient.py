import random

UNIT = {
    'hr': 'bpm',
    'spo2': '%'
}

RESTING = {
    'hr': (60, 80),
    'spo2': (95, 99)
}

EXCITED = {
    **RESTING,
    'hr': (70, 100)
}

UNWELL = {
    'hr': (50, 70),
    'spo2': (80, 94)
}

class Patient:
    """
    Simulates patient's state based on her general condition.
    """

    def __init__(self, id, *, condition=RESTING):
        self.id = id
        self.condition = condition
        self.state = {}
        self.tick()

    def tick_param(self, current, minV, maxV):
        value = current + random.randint(-1, 1)
        if value < minV:
            value = minV
        elif value > maxV:
            value = maxV
        return value

    def tick(self):
        """
        Randomly modifies patient's state based on her condition.
        """
        for param, (minV, maxV) in self.condition.items():
            try:
                value = self.tick_param(self.state[param], minV, maxV)
            except KeyError:
                value = random.randint(minV, maxV)
            self.state[param] = value

    @property
    def params(self):
        return iter(self.condition)

    def measure(self, param):
        return self.state[param]
