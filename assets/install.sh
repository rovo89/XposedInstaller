#!/system/bin/sh
export PATH=/system/bin:$PATH

BUSYBOX=./busybox-xposed

CP="$BUSYBOX cp"
CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
CUT="$BUSYBOX cut"
GREP="$BUSYBOX grep"
MKDIR="$BUSYBOX mkdir"
MOUNT="$BUSYBOX mount"
RM="$BUSYBOX rm"
TEST="$BUSYBOX test"
TOUCH="$BUSYBOX touch"

if [ ! -f app_process -o ! -f XposedBridge.jar ]; then
	echo Files for update not found!
	pwd
	exit 1
fi

echo Mounting /system writable...
$MOUNT -o remount,rw /system || exit 1

if [ -f /system/bin/app_process.orig ]; then
	echo Backup of app_process executable exists already at /system/bin/app_process.orig
else
    $CP -a /system/bin/app_process /system/bin/app_process.orig || exit 1
    echo Created backup of app_process executable at /system/bin/app_process.orig
fi

echo Copying app_process...
$CP app_process /system/bin/app_process || exit 1
$CHMOD 755 /system/bin/app_process
$CHOWN root:shell /system/bin/app_process

echo Getting user id for Xposed Installer...
XPOSEDUSER=`$GREP '^de.robv.android.xposed.installer ' /data/system/packages.list | $CUT -d' ' -f2`
echo User id: $XPOSEDUSER
$TEST -n "$XPOSEDUSER" || exit 1

if [ ! -d /data/xposed ]; then
	echo Creating /data/xposed...
	$MKDIR /data/xposed
	$CHMOD 755 /data/xposed
	$CHOWN root:shell /data/xposed
fi

if [ ! -d /data/xposed/lib ]; then
	echo Creating /data/xposed/lib...
	$MKDIR /data/xposed/lib
	$CHMOD 755 /data/xposed/lib
	$CHOWN $XPOSEDUSER:shell /data/xposed/lib
fi

echo Copying XposedBridge.jar...
$CP XposedBridge.jar /data/xposed/XposedBridge.jar.newversion || exit 1
$CHMOD 644 /data/xposed/XposedBridge.jar.newversion
$CHOWN root:shell /data/xposed/XposedBridge.jar.newversion

if [ -f /data/xposed/disabled ]; then
	echo Removing /data/xposed/disabled...
	$RM /data/xposed/disabled
fi

echo Touching module lists...
$TOUCH /data/xposed/modules.list /data/xposed/modules.whitelist || exit 1
$CHMOD 644 /data/xposed/modules.list /data/xposed/modules.whitelist
$CHOWN $XPOSEDUSER:shell /data/xposed/modules.list /data/xposed/modules.whitelist

echo
echo Done! Changes will become active on reboot.
exit 0
