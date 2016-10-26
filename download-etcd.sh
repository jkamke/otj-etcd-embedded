#!/bin/bash -ex
# nullglob is a bashism

shopt -s nullglob

RELEASE=3.0.13
DEST=${1:-target/generated-resources/etcd}
mkdir -p $DEST
cd $DEST

download() {
    URL=https://github.com/coreos/etcd/releases/download/v${RELEASE}/etcd-v${RELEASE}-${2}
    OUT=etcd-${1}
    if [ ! -e $OUT ]
    then
        rm -rf tmp
        mkdir tmp
        pushd tmp
        curl -SfL -o dl-etcd "$URL"
        if [[ $2 == *zip ]]
        then
            unzip dl-etcd
        else
            tar xzf dl-etcd
        fi
        mv etcd-*/{etcd,etcd.exe} ../$OUT
        popd
        rm -rf tmp
    fi
}

download linux-amd64 linux-amd64.tar.gz
download macosx-x86_64 darwin-amd64.zip
download windows-amd64 windows-amd64.zip
