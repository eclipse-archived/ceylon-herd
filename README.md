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

The content of this repository is released under the AGPL V3
as provided in the LICENSE file that accompanied this code.
