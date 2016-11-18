## Overview ##

"Neurons to Algorithms" (N2A) is a language for modeling neural systems, along with a software tool for editing models and simulating them. For an introduction to the concepts behind N2A, see the paper [N2A: a computational tool for modeling from neurons to algorithms](http://www.frontiersin.org/Neural_Circuits/10.3389/fncir.2014.00001/abstract). For the current definition of the language, see the [Language Reference](../../wiki/LanguageOverview.md) page on our wiki.

The N2A language represents systems in a parts-relations framework combined with dynamics. Each part has as set of equations that define its state variables and how they evolve over time. Parts are connected to other parts, like vertices are connected by edges in a graph. Connections also have equations that couple the variables in the parts.

Each part is really a pattern for a population of instances. Each instance has its own unique copy of the state variables, and evolves independently of the other instances of that part. For example, a part that defines a pyramidal cell in the cortex may be duplicated millions or billions of times. Connections between parts turn into connections between specific pairs of instances, following whatever association rules you define.

In addition to the fairly typical concepts described above, N2A gives you the ability to change crucial attributes of the part itself. For example, you can change the duration of simulation time-steps on an individual instance, and thus have different parts of your simulation running a different frequencies. (Provided, of course, that your simulator supports this.)

You can tell an instance to change into another kind of part, or you can split an instance into several new instances of arbitrary type. This enables simulations of development, neurogenesis, and dendritic growth.

## Development Status ##

The N2A tool is almost ready for a 1.0 release. See [Issues "Release 1.0"](../../issues?q=is%3Aopen+is%3Aissue+milestone%3A"Release+1.0") for work remaining.

## Download ##

[Installation](../../wiki/Installation.md) -- How to download and install N2A, and set up at least one simulator.

[Getting Started](../../wiki/GettingStarted.md) -- Run a simple "Hello World" example: the Hodgkin-Huxley cable equations.

[Contributing Code](../../wiki/DeveloperHowTo.md) -- How to set up a development environment, if you would like to volunteer on this project or simply wish to build from source.
