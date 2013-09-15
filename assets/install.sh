#!/system/bin/sh
export PATH=/system/bin:$PATH

cd /data/data/de.robv.android.xposed.installer/
BUSYBOX=cache/busybox-xposed

BRACKET="$BUSYBOX ["
CP="$BUSYBOX cp"
CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MOUNT="$BUSYBOX mount"
RM="$BUSYBOX rm"

if $BRACKET ! -f cache/app_process  ]; then
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
$CP cache/app_process /system/bin/app_process || exit 1
$CHMOD 755 /system/bin/app_process || exit 1
$CHOWN root:shell /system/bin/app_process || exit 1

if $BRACKET -f conf/disabled ]; then
	echo Removing conf/disabled...
	$RM conf/disabled || exit 1
fi

if $BRACKET -d /data/xposed ]; then
	echo Deleting legacy directory /data/xposed...
	$RM -r /data/xposed/
fi

echo
echo Done! Changes will become active on reboot.
exit 0
