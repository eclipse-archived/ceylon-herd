#! /bin/sh
#
# Startup script for ceylon-herd.
#
# Stephane Epardaud <stephane@epardaud.fr>
# Copyright 2010, Stephane Epardaud
#

### BEGIN INIT INFO
# Provides: ceylon-herd
# Required-Start: $local_fs $network $named
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
PLAY=/usr/share/play/1.2.4/play
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

. /lib/lsb/init-functions

if test -f /etc/default/$APP_NAME
then
	. /etc/default/$APP_NAME
fi

test -d $APP_PATH || exit 0

if test "$IS_HERD_CONFIGURED" = 0
then
	log_failure_msg "You must configure $APP_NAME in /etc/default/$APP_NAME before running it"
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

set -e

case "$1" in
    start)
        log_daemon_msg "Starting $DESC" $APP

		if start-stop-daemon --start --pidfile $PLAY_PID_PATH --user $USER --chdir $APP_PATH --chuid $USER:$USER --quiet --exec $PLAY -- start $APP_PATH $PLAY_ARGS > /dev/null
		then
			log_end_msg 0
		else
			log_failure_msg "failed (already running?)."
			log_end_msg 1
			exit 1
		fi
	;;

    stop)
		log_daemon_msg "Stopping $DESC" $APP
		if start-stop-daemon --stop --oknodo --pidfile $PLAY_PID_PATH --user $USER --chdir $APP_PATH --chuid $USER:$USER --quiet --retry 5
		then
			test -f $PLAY_PID_PATH && rm $PLAY_PID_PATH
			log_end_msg 0
		else
			log_failure_msg "failed (not running?)."
			log_end_msg 1
			exit 1
		fi
	;;

    restart|force-reload)
        $0 stop
        $0 start
    ;;

    status)
	if test -f $PLAY_PID_PATH
	then
		PID=$(cat $PLAY_PID_PATH)
		if (grep '(java)' /proc/$PID/stat && grep play /proc/$PID/cmdline) > /dev/null
		then
			log_success_msg "$DESC is running"
			exit 0
		else
			log_success_msg "$DESC is not running"
			exit 3
		fi
	else
		log_success_msg "$DESC is not running"
		exit 3
	fi
    ;;

    *)
        log_action_msg "Usage: $0 {start|stop|restart|force-reload|status}" >&2
        exit 1
    ;;
esac

exit 0
