#! /bin/sh
#
# ceylon-herd  Startup script for the Ceylon Herd module repository
#
# chkconfig: - 90 10
# description: Ceylon Herd module repository
# processname: ceylon-herd
# config: /etc/ceylon-herd/application.conf
# pidfile: /var/run/ceylon-herd/ceylon-herd.pid
# Startup script for ceylon-herd.
#
# Stephane Epardaud <stephane@epardaud.fr>
# Copyright 2010, Stephane Epardaud
#

### BEGIN INIT INFO
# Provides: ceylon-herd
# Required-Start: $local_fs $network $named httpd
# Required-Stop: $local_fs $network $named
# Default-Start:  2 3 4 5
# Default-Stop: 0 1 6
# Short-Description: start and stop ceylon-herd
# Description: ceylon-herd application
### END INIT INFO

APP=ceylon-herd
USER=ceylherd
CLUSTER=

PATH=/sbin:/bin:/usr/sbin:/usr/bin
PLAY=/usr/share/play/1.2.6/play
PLAY_ID=
PLAY_ARGS=

if test -n "$CLUSTER"
then
	APP_FOLDER=$APP/$CLUSTER
	APP_NAME=$APP-$CLUSTER
	PLAY_ID=prod-$CLUSTER
else
	APP_FOLDER=$APP
	APP_NAME=$APP
fi

APP_PATH=/usr/share/$APP_FOLDER
PLAY_PID_FOLDER=/var/run/$APP_FOLDER
export PLAY_PID_PATH=$PLAY_PID_FOLDER/$APP.pid
export PLAY_LOG_PATH=/var/log/$APP_FOLDER
DESC="$APP_NAME application"

. /etc/rc.d/init.d/functions

if test -f /etc/default/$APP_NAME
then
	. /etc/default/$APP_NAME
fi

test -d $APP_PATH || exit 0

if test "$IS_HERD_CONFIGURED" = 0
then
	echo "You must configure $APP_NAME in /etc/default/$APP_NAME before running it"
	failure "You must configure $APP_NAME in /etc/default/$APP_NAME before running it"
	case "$1" in
    	stop)
    	exit 0
    ;;
    *)
		exit 1
	;;
	esac
fi

# setup the id
if test -n "$PLAY_ID"
then
	PLAY_ARGS="$PLAY_ARGS --%$PLAY_ID"
fi

# make sure the PID can be written
if test \! -d $PLAY_PID_FOLDER
then
	mkdir -p $PLAY_PID_FOLDER
	chown $USER.$USER $PLAY_PID_FOLDER
fi

is_running() {
	if test -f $PLAY_PID_PATH
	then
		PID=$(cat $PLAY_PID_PATH)
		if test -f /proc/$PID/stat
		then
			if (grep '(python)' /proc/$PID/stat && grep play /proc/$PID/cmdline) > /dev/null
			then
				echo "$DESC is running"
				return 0
			else
				echo "$DESC is not running"
				# stale PID file
				rm -f $PLAY_PID_PATH
				return 3
			fi
		else
			echo "$DESC is not running"
			# stale PID file
			rm -f $PLAY_PID_PATH
			return 3
		fi
	else
		echo "$DESC is not running"
		return 3
	fi
}

case "$1" in
    start)
        echo -n "Starting $DESC "

	if is_running > /dev/null
	then
		echo "failed (already running?)."
		failure "failed (already running?)."
		exit 1
	else
		# Make it run
		runuser -s /bin/sh ceylherd -c "cd $APP_PATH; $PLAY run $PLAY_ARGS > /dev/null & echo \$! > $PLAY_PID_PATH"
		if [ "$?" -eq 0 ]
		then
			echo
		else
			echo "failed (failed to run?)."
			failure "failed (failed to run?)."
			exit 1
		fi
	fi
	;;

    stop)
		echo -n "Stopping $DESC "
		if is_running > /dev/null && killproc -p $PLAY_PID_PATH $PLAY
		then
			test -f $PLAY_PID_PATH && rm $PLAY_PID_PATH
			echo
		else
			echo "failed (not running?)."
			failure "failed (not running?)."
			exit 1
		fi
	;;

    restart|force-reload)
        $0 stop
        $0 start
    ;;

    status)
	if is_running
	then exit 3
	else exit 0
	fi
    ;;

    *)
        echo "Usage: $0 {start|stop|restart|force-reload|status}" >&2
        exit 1
    ;;
esac

exit 0
