#!/system/bin/sh
export PATH=/system/bin:$PATH

cd `dirname $0`

if [ ! -f app_process -o ! -f XposedBridge.jar ]; then
	echo Files for update not found!
	pwd
	exit 1
fi

echo Mounting /system writable...
mount -o remount,rw /system || exit 1

if [ -f /system/bin/app_process.orig ]; then
	echo Backup of app_process executable exists already at /system/bin/app_process.orig
else
    cp -a /system/bin/app_process /system/bin/app_process.orig || exit 1
    echo Created backup of app_process executable at /system/bin/app_process.orig
fi

echo Copying app_process...
cp app_process /system/bin/app_process || exit 1
chmod 755 /system/bin/app_process
chown 0 /system/bin/app_process
chgrp 2000 /system/bin/app_process

echo Getting user id for Xposed Installer...
XPOSEDUSER=`grep '^de.robv.android.xposed.installer ' /data/system/packages.list | cut -d' ' -f2`
echo User id: $XPOSEDUSER
test -n "$XPOSEDUSER" || exit 1

if [ ! -d /data/xposed ]; then
	echo Creating /data/xposed...
	mkdir /data/xposed
	chmod 755 /data/xposed
	chown 0 /data/xposed
	chgrp 2000 /data/xposed
fi

if [ ! -d /data/xposed/lib ]; then
	echo Creating /data/xposed/lib...
	mkdir /data/xposed/lib
	chmod 755 /data/xposed/lib
	chown $XPOSEDUSER /data/xposed/lib
	chgrp 2000 /data/xposed/lib
fi

echo Copying XposedBridge.jar...
cp XposedBridge.jar /data/xposed/XposedBridge.jar.newversion || exit 1
chmod 644 /data/xposed/XposedBridge.jar.newversion
chown 0 /data/xposed/XposedBridge.jar.newversion
chgrp 2000 /data/xposed/XposedBridge.jar.newversion

if [ -f /data/xposed/disabled ]; then
	echo Removing /data/xposed/disabled...
	rm /data/xposed/disabled
fi

echo Touching module lists...
touch /data/xposed/modules.list || exit 1
chmod 644 /data/xposed/modules.list
chown $XPOSEDUSER /data/xposed/modules.list
chgrp 2000 /data/xposed/modules.list
touch /data/xposed/modules.whitelist || exit 1
chmod 644 /data/xposed/modules.whitelist
chown $XPOSEDUSER /data/xposed/modules.whitelist
chgrp 2000 /data/xposed/modules.whitelist

echo
echo Done! Changes will become active on reboot.
exit 0
