#!/bin/sh

VERSION=1.22

tar --transform "s,^,ceylon-herd-$VERSION/," -zcvf ../ceylon-herd-$VERSION.tar.gz *
