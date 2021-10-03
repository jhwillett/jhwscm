#/bin/sh
#
# jhwscm/shell.sh
#
# Copyright (C) 2010,2021 Jesse H. Willett
# MIT License (see jhwscm/LICENSE.txt)

java -cp build/base/classes:build/base/test:build/firm/classes:build/firm/test:build/shell/classes:build/shell/test Shell "$@"
