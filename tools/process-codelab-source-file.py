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

# Called by the script process-codelab-source-files.sh.
# Argument is the path to the source file to process.

import re
import sys

if len(sys.argv) < 2:
    sys.exit("Error: Please specify the name of the file to process.")

file = sys.argv[1]

codelabStartPattern = '// CODELAB: '
codelabEndPattern = '// CODELAB SECTION END'

lines = []

with open(file, 'rt') as infile:
    discard = False
    for line in infile:
        if discard:
          if codelabEndPattern in line:
            discard = False
        else:
          lines.append(line)
          if codelabStartPattern in line:
                discard = True

with open(file, 'w') as outfile:
    for line in lines:
        outfile.write(line)