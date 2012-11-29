#!/system/bin/sh
export PATH=/system/bin:$PATH

setprop ctl.restart surfaceflinger
setprop ctl.restart zygote
