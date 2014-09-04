# How to run the Ceylon Herd Module Repository

1. [Download Play Framework 1.2.4](http://download.playframework.org/releases/play-1.2.4.zip) and install it
1. Clone the [Herd repository](https://github.com/ceylon/ceylon-herd)
1. Open a shell and go to the `ceylon-herd` directory you just cloned
1. Run `play dependencies` to download the required modules
1. Create your Postgres DB
    1. sudo su - postgres 
    1. createuser -PSRD ceylon-herd
    1. _enter `ceylon-herd` as password when prompted_
    1. createdb -O ceylon-herd -E utf8 ceylon-herd
    1. exit
1. Run the application
    1. play run

# How to make your user (if registration is disabled, which is the default for now)

This can only be done by hand for now:

1. Pick a password
1. Generate a UUID, which we'll use as password salt
    1. You can [make one online](http://www.famkruithof.net/uuid/uuidgen): select `Version 4 (random)`
1. In a shell, generate your salted SHA1 password by concatenating your UUID (the salt) with your password
    1. `echo -n YOUR_UUIDYOUR_PASSWORD | sha1sum | awk '{print $1}'` (no space between the salt and password)
    1. Copy the resulting SHA signature
1. Open a `psql` console to your database:
    1. `psql -h localhost -U ceylon-herd`
1. Add your user (as admin)
    1. `INSERT INTO user_table  (id, email, firstname, admin, lastname, salt, password, status, username) VALUES ((select nextval('hibernate_sequence')), 'email@example.org', 'FirstName', true, 'LastName', 'YOUR_SALT', 'YOUR_SHA1', 'REGISTERED', 'UserName');`

# How to make your user admin (if registration is enabled)

This can only be done by hand for now:

1. Register your user at http://localhost:9000/register
1. See in the logs in the console what your activation link is, and follow it (in DEV mode no mail is sent)
1. Complete your registration
1. Open a `psql` console to your database:
    1. `psql -h localhost -U ceylon-herd`
1. Set yourself as admin
    1. `UPDATE user_table SET admin = true WHERE username = 'your-user-name';`

# Troubleshooting

1. If you get the error `FATAL: Ident authentication failed for user "ceylon-herd"` look [here for a possible solution](http://www.cyberciti.biz/faq/psql-fatal-ident-authentication-failed-for-user/)

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



