VERSION=1.0.0-SNAPSHOT
TARGET=$1

function copy {
  MODULE="$1"
  BUNDLE="$MODULE-$VERSION.jar"
  cp $MODULE/target/$BUNDLE $TARGET/osgi/equinox/bundles
  echo "bundles/$BUNDLE@start, \\" >> $TARGET/osgi/equinox/config-template/config.ini  
}

if [ ! -e $TARGET/osgi/equinox ] 
then
    echo Directory structure not as expected in $TARGET
    echo Expected at least an osgi/equinox directory in that location
    exit
fi

copy cloud-prov-remsvc

# This one will most likely be overwritten by a subsequent script
cp $TARGET/osgi/equinox/config-template/config.ini $TARGET/osgi/equinox/config/config.ini

