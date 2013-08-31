#!/system/bin/sh
export PATH=/system/bin:$PATH

BUSYBOX=./busybox-xposed

BRACKET="$BUSYBOX ["
CP="$BUSYBOX cp"
CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MKDIR="$BUSYBOX mkdir"
MOUNT="$BUSYBOX mount"
RM="$BUSYBOX rm"
TOUCH="$BUSYBOX touch"

XPOSEDUSER=$1
if $BRACKET -z "$XPOSEDUSER" ]; then
	echo This script needs the user id for the Xposed Installer as first argument!
	exit 1
fi

if $BRACKET ! -f app_process -o ! -f XposedBridge.jar ]; then
	echo Files for update not found!
	pwd
	exit 1
fi

echo Mounting /system writable...
$MOUNT -o remount,rw /system

if $BRACKET -f /system/bin/app_process.orig ]; then
	echo Backup of app_process executable exists already at /system/bin/app_process.orig
else
    $CP -a /system/bin/app_process /system/bin/app_process.orig || exit 1
    echo Created backup of app_process executable at /system/bin/app_process.orig
fi

echo Copying app_process...
$CP app_process /system/bin/app_process || exit 1
$CHMOD 755 /system/bin/app_process || exit 1
$CHOWN root:shell /system/bin/app_process || exit 1

if $BRACKET ! -d /data/xposed ]; then
	echo Creating /data/xposed...
	$MKDIR /data/xposed || exit 1
	$CHMOD 755 /data/xposed || exit 1
	$CHOWN root:shell /data/xposed || exit 1
fi

echo Copying XposedBridge.jar...
$CP XposedBridge.jar /data/xposed/XposedBridge.jar.newversion || exit 1
$CHMOD 644 /data/xposed/XposedBridge.jar.newversion || exit 1
$CHOWN root:shell /data/xposed/XposedBridge.jar.newversion || exit 1

echo Copying app_process for future ROM updates...
$CP /system/bin/app_process /data/xposed/app_process || exit 1
$CHMOD 644 /data/xposed/app_process || exit 1
$CHOWN root:shell /data/xposed/app_process || exit 1

if $BRACKET -f /data/xposed/disabled ]; then
	echo Removing /data/xposed/disabled...
	$RM /data/xposed/disabled || exit 1
fi

echo Touching module lists...
$TOUCH /data/xposed/modules.list /data/xposed/modules.whitelist || exit 1
$CHMOD 644 /data/xposed/modules.list /data/xposed/modules.whitelist || exit 1
$CHOWN $XPOSEDUSER:shell /data/xposed/modules.list /data/xposed/modules.whitelist || exit 1

echo
echo Done! Changes will become active on reboot.
exit 0
