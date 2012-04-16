# How to run the Ceylon Herd Module Repository

1. [Download Play Framework 1.2.4](http://download.playframework.org/releases/play-1.2.4.zip) and install it
1. Run `play dependencies` to download the required modules
1. Create your Postgres DB
    1. sudo su - postgres 
    1. createuser -PSRD ceylon-herd
    1. _enter `ceylon-herd` as password when prompted_
    1. createdb -O ceylon-herd -E utf8 ceylon-herd
    1. exit
1. Run the application
    1. play run

# How to make your user admin

This can only be done by hand for now:

1. Register your user at http://localhost:9000/register
1. See in the logs in the console what your activation link is, and follow it (in DEV mode no mail is sent)
1. Complete your registration
1. Open a `psql` console to your database:
    1. `psql -h localhost -U ceylon-herd`
1. Set yourself as admin
    1. `UPDATE user_table SET admin = true WHERE username = 'your-user-name';`

# License

The content of this repository is released under AGPLv3 as provided in
the LICENSE file that accompanied this code, with the following
clarifications.

The AGPL does not extend to the files in the public/ directory. Such
files are licensed as indicated in the file or else are dedicated to
the public domain to the maximum extent possible under applicable law.

The AGPL does not extend to any dependencies that we do not distribute
in our github repository or which are indicated in our github
repository as being under some other license, even if
AGPL-compatible. For example, if you create and publicly deploy a
modified version of Ceylon Herd that is based on the Play framework,
the AGPL does not extend to any of the dependencies that make up the
Play framework.

Compliance with the source code requirements of section 13 of AGPLv3
is satisfied by storing your modified version in a public revision
control repository and prominently providing your users with notice of
the location of this repository.



