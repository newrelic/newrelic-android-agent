#!/bin/bash -e

LICENSE_DIR="lcheck"
PYTHON="/usr/local/python27/bin/python"

if [ ! -x $PYTHON ]; then
	PYTHON=`which python2.7`
fi
if [ -z "$PYTHON" ]; then
	echo "No python 2.7; validation cannot run"
	exit 1
fi

#####
# Validate License
#####
if [ -d "$LICENSE_DIR" ]; then
  echo "Updating the license validator..."
  ( cd $LICENSE_DIR && git pull --rebase )
else
  echo "Getting the license validator..."
  mkdir $LICENSE_DIR
  git clone git@source.datanerd.us:newrelic/license_reviewer.git $LICENSE_DIR/
fi

# be sure to update our docs templates
cp $LICENSE_DIR/LicenseReviewer/*.erb LicenseData/

echo "Checking license compliance... (You can add the -i argument to this script to do this interactively)"
LICENSE_REVIEWER_METAFILE_PATH=./LicenseData $PYTHON $LICENSE_DIR/LicenseReviewer/license_reviewer.py review $1
STATUS=$?

echo ""

if [ $STATUS -eq 0 ] && [ -d ../docs ];then
	echo "Licenses are up to date, generating docs!"
	LICENSE_REVIEWER_METAFILE_PATH=./LicenseData $PYTHON $LICENSE_DIR/LicenseReviewer/license_reviewer.py geninstallerdoc LICENSE
	LICENSE_REVIEWER_METAFILE_PATH=./LicenseData $PYTHON $LICENSE_DIR/LicenseReviewer/license_reviewer.py gendocssitedoc ../docs/articles/licenses/android-agent-licenses.md.erb
	echo "Done! Check ./LICENSE and ../docs/articles/licenses/android-agent-licenses.md.erb for license updates that may need committed."
else
  echo "Licenses are out of date! Please talk to Jonathan K about fixing this."
	exit $STATUS
fi

exit 0
