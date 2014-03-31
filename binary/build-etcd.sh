#!/bin/bash

set -o errexit -o nounset -o xtrace

[ -e etcd ] || git clone -q https://github.com/coreos/etcd etcd

cd etcd
git reset --hard $1

./build
mkdir -p $2/bin
cp bin/etcd $2/bin/
