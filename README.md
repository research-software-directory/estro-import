This repo contains a script for importing ESTRO software from a CSV file. 

This script follows a standard Maven setup. The CSV file can be found in the resources.

Before this script is ran, an RSD must be running with a community with slug `estro`.

The Java program expects two arguments, which are in order:

1. The full domain of the RSD to which to push the software to, e.g. `http://localhost` or `https://research-software.dev`
1. The value of the env variable PGRST_JWT_SECRET, so that an admin JWT can be created
