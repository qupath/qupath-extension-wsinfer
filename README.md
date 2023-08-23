# QuPath extension WSInfer

This repo contains the extension to work with WSInfer models in QuPath.

This helps make deep learning-based patch classification in pathology images easy and interactive.

See https://wsinfer.readthedocs.io for details.

> **If you use this extension, please cite both the WSInfer preprint and the [QuPath paper](https://qupath.readthedocs.io/en/0.4/docs/intro/citing.html)!**

## Installation

Download the latest version of the extension from the [releases page](https://github.com/qupath/qupath-extension-wsinfer/releases).

Then drag & drop the downloaded .jar file onto the main QuPath window to install it.

## Usage

The WSInfer extension adds a new menu item to QuPath's **Extensions** menu, which can be used to open a WSInfer dialog.

The dialog tries to guide you through the main steps, from top to bottom.

Briefly: after selecting a WSInfer model, you'll need to select one or more tiles to use for inference.
The easiest way to do this is generally to draw an annotation, and leave it up to QuPath to create the tiles.

Pressing run will download the model and PyTorch (if necessary), then run the model across the tiles.

You can see the results in the form of measurement maps, as a results table, or as colored tiles in the QuPath viewer.

> Tip: To see the tiles properly, you'll need to ensure that they are both displayed and filled in the viewer (i.e. ensure the two buttons showing three green objects are selected).

## Additional options

It's worth checking out the *Additional options* to see where models will be stored.

You can also use this to select whether inference should use the CPU or GPU - if a GPU is available and compatible.

> GPU acceleration is selected by choosing *MPS* on an Apple Silicon Mac, for *Metal Performance Shaders*.