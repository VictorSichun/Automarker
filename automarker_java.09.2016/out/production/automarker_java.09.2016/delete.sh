#!/bin/bash

# example delete submission for automarker 

mysql -u cs_automarker -p -h helix-gate.sit.auckland.ac.nz cs_automarker 

# select * from am_submissions where username='adun198' and problem_id=362;
# delete from am_submissions where username='adun198' and submission_id=89187;
#
# delete from am_submissions where username='mdin003' and problem_id=171;
#
# mark sumbission status to 'other'
#select * from am_submissions where username='yliu948' and problem_id=369;
#update am_submissions set status_code=7 where username='yliu948' and submission_id=89427;
