#!/bin/bash

# Local environment version of run.sh
# Uses local PostgreSQL installation instead of /modules/cs258/bin/psql

PSQL_CMD="/opt/homebrew/opt/postgresql@16/bin/psql"

resetAndLoadData(){
	# echo "DROP DATABASE cwk; CREATE DATABASE cwk;" | $PSQL_CMD postgres

	echo "Resetting cwk schema with schema.sql"
	$PSQL_CMD -d cwk -q -v ON_ERROR_STOP=1 < schema.sql || exit 1
	echo "Resetting cwk schema with reset-data.sql"
	$PSQL_CMD -d cwk -q < reset-data.sql  || exit 1
	echo "Inserting test data from $1"

	cat <(echo "SET session_replication_role = 'replica';") $1 <(echo "SET session_replication_role = 'origin';") | $PSQL_CMD -d cwk -q 
}

if [ $# -eq 0 ]
then 
    mvn -e -q compile exec:java@gig
else
    if [ $1 == "reset" ]
    then
        D=$(date +"%Y%m%d-%H%M%S")
	if [ "$2" == "-f" ]
	then
		resetAndLoadData $3
	else
		#If a number was provided, generate data (based on the provided seed number)
		if [ "$2" == "-r" ]
		then
			test -d tmp || mkdir tmp
			echo "Generating test data based on seed $3"
   		     	mvn -e -q compile exec:java@test -Dexec.args="reset $3" > tmp/testData-$D-$3.sql
			resetAndLoadData tmp/testData-$D-$3.sql
		fi

	fi
    elif [ $1 == "test" ]
    then
        mvn -e -q compile exec:java@test -Dexec.args="test $2"
    fi
fi

