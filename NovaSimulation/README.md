# Requirements:

- python >= 3.8


# Setup:


Once python is installed install all of the requirements by running:

```pip install -r requirements.txt```

# Usage:


While in `simulation/` directory run:

 ```python simulator.py <option> <optional path>```

The options are following:


- mesh - displays basic connections between devices
- health - displays emergency simulation, real time animation showing patient's parameters gradually getting worse, and what happens when they reach a certain threshold. Displays two paths (main and backup) that a message has to make to reach monitoring station.
- gengrid - used to generate random grids, network is self-organising and it adapts itself to the situation
- distplot - displays map built only by using measured distanced from each device to every other device. Measures were taken using real devices.

Path parameter is optional, use `random.txt` for randomly generated grids.