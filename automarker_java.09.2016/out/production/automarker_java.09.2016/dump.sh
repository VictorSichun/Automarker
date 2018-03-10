#!/bin/bash

# dumps current automarker database tables 

mysqldump -u cs_automarker -p --single-transaction \
     	  -h helix-gate.sit.auckland.ac.nz cs_automarker \
 am_assignments am_courses am_problems am_usermappings am_users \
 > cs_automarker.sql

# am_assignments am_courses am_problems am_submissions am_usermappings am_users \
