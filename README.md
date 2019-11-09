[![](https://img.shields.io/badge/python-3.4%20|%203.5%20|%203.6%20|%203.7%20|%203.8-blue.svg)](https://www.python.org/download/) ![t](https://img.shields.io/badge/maintained-yes-green.svg) 

Current status of PFA conformance tests for [Python 3 implementation of Titus]( https://github.com/animator/python3-titus.git) - [![Build Status](https://travis-ci.org/animator/pfa.svg?branch=master)](https://travis-ci.org/animator/pfa)

Specification of the Portable Format for Analytics (PFA). See the [documentation website](http://dmg.org/pfa/index.html) for more information.

## To run the [Titus conformance test suite](http://dmg.org/pfa/docs/conformance/)
1) Clone this repository.
2) Download [conformance tests file (238 MB)](http://github.com/datamininggroup/pfa/releases/download/0.8.1/pfa-tests.json) and replace `conformance-tests/pfa-tests.json` with the downloaded file.
3) Install Titus for Python 3 using `pip install git+https://github.com/animator/python3-titus.git`
4) Run `cd conformance-tests`  
5) Run `python runTestTitus.py pfa-tests.json`

In case of any issues please raise it [here](https://github.com/animator/pfa/issues)!
