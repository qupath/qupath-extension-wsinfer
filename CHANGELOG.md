# Changelog

## v0.3.0

* Compatibility with QuPath v0.5 (now the minimum version required)
* Enable auto-updating the extension (https://github.com/qupath/qupath-extension-wsinfer/issues/40)
* Support for local models (https://github.com/qupath/qupath-extension-wsinfer/issues/32)
* Show model information, where available
* Code refactoring and UI improvements
* Show tile predictions per second to help with performance tuning
* Support for custom batch sizes
  * Can substantially improve performance with GPU acceleration
  * Requires PyTorch 2.0.1 or later for Apple Silicon MPS

## v0.2.1

* Fix CPU/GPU support (https://github.com/qupath/qupath-extension-wsinfer/issues/41)

## v0.2.0

* [Preprint on arXiv](https://arxiv.org/abs/2309.04631)
* [Documentation on ReadTheDocs](https://qupath.readthedocs.io/en/0.4/docs/deep/wsinfer.html)
* Faster tiling for complex regions (https://github.com/qupath/qupath-extension-wsinfer/pull/34)
* Searchable combo box for model selection (https://github.com/qupath/qupath-extension-wsinfer/pull/31)
* Improved support for offline use (once models are downloaded) (https://github.com/qupath/qupath-extension-wsinfer/pull/30)

## v0.1.0

* First release!
