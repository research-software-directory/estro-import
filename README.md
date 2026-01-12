This repo contains a script for importing ESTRO software from a CSV file. 

This script follows a standard Maven setup. The CSV file can be found in the resources.

The Java program expects two arguments, which are in order:

1. The full domain of the RSD to which to push the software to, e.g. `http://localhost` or `https://research-software.dev`
1. A user-generated API access token (maybe we should change this into an admin token, as this will make the user a maintainer of all the software pages)
