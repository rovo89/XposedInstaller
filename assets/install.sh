#!/sbin/sh
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
chown root:shell /system/bin/app_process

if [ ! -d /data/xposed ]; then
	echo Creating /data/xposed...
	mkdir /data/xposed
	chmod 755 /data/xposed
	chown root:shell /data/xposed
fi

echo Copying XposedBridge.jar...
cp XposedBridge.jar /data/xposed/XposedBridge.jar.newversion || exit 1
chmod 644 /data/xposed/XposedBridge.jar.newversion
chown root:shell /data/xposed/XposedBridge.jar.newversion

echo Touching module list...
XPOSEDUSER=`grep '^de.robv.android.xposed.installer ' /data/system/packages.list | cut -d' ' -f2`
touch /data/xposed/modules.list || exit 1
chmod 644 /data/xposed/modules.list
chown 0$XPOSEDUSER:shell /data/xposed/modules.list

echo Mounting /system read-only...
mount -o remount,ro /system

echo
echo Done! Changes will become active on reboot.
exit 0
