#!/bin/sh

VERSION=1.15

tar --transform "s,^,ceylon-herd-$VERSION/," -zcvf ../ceylon-herd-$VERSION.tar.gz *
