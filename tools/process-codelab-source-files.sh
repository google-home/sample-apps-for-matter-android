#/bin/sh

#
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Gets the name of all the files that have at least one codelab section
# and invokes the script process-codelab-source-file.py to remove the
# code from the codelab sections.
#
# Arguments
#   1. repoRootPath [optional]
#      Path to the root of the sample app repo.
#      If not specified, defaults to the directory where the command is run.
#
# Sample usage:
#   If at the root of the sample app repo:
#      ./tools/process-codelab-source-files.sh
#   From anywhere:
#      <repoRootPath>/tools/process-codelab-source-files.sh <repoRootPath>

realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

if [ $# -eq 0 ]
then
  root=`pwd`
elif [ $# -eq 1 ]
then
  root=$1
else
  echo "Error: Usage is $0 [repoRootPath]"
  exit 1
fi

echo "root [$root]"

for file in $(grep -rFl --include "*.kt" "// CODELAB: ")
do
  realFilePath=`realpath $file`
  echo "Processing $realFilePath ..."
  python3 ${root}/tools/process-codelab-source-file.py $realFilePath
done
