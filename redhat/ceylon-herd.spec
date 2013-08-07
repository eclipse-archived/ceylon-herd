%define	herd_install_path	/usr/share/%name
%define herd_etc_path		/etc/%name
%define herd_log_path		/var/log/%name
%define herd_lib_path		/var/lib/%name
%define herd_run_path		/var/run/%name
%define herd_repo_path		/var/www/modules.ceylonlang/repo
%define herd_uploads_path	/var/www/modules.ceylonlang/uploads
%define herd_user		ceylherd

Name:		ceylon-herd
Version:	1.13
Release:	1%{?dist}
Summary:	Ceylon Herd module repository

Group:		Applications/Publishing
License:	AGPL
URL:		http://ceylon-lang.org
Source0:	ceylon-herd-%{version}.tar.gz
BuildRoot:	%(mktemp -ud %{_tmppath}/%{name}-%{version}-%{release}-XXXXXX)
BuildArch:  noarch

Requires:	play-1.2.6, postgresql, redhat-lsb

%description
Ceylon Herd module repository

%prep
%setup -q

%build


%install
rm -rf $RPM_BUILD_ROOT
install -d $RPM_BUILD_ROOT/%{herd_install_path} $RPM_BUILD_ROOT/%{herd_etc_path} \
           $RPM_BUILD_ROOT/%{herd_log_path} $RPM_BUILD_ROOT/%{herd_lib_path} \
           $RPM_BUILD_ROOT/%{herd_run_path} $RPM_BUILD_ROOT/etc/default \
           $RPM_BUILD_ROOT/etc/init.d $RPM_BUILD_ROOT/etc/logrotate.d
rsync --exclude .git -r app lib public modules $RPM_BUILD_ROOT/%{herd_install_path}/
cp conf/* $RPM_BUILD_ROOT/%{herd_etc_path}/
cp debian/ceylon-herd.default $RPM_BUILD_ROOT/etc/default/%{name}
cp redhat/ceylon-herd.init.d $RPM_BUILD_ROOT/etc/init.d/%{name}
rm $RPM_BUILD_ROOT/%{herd_etc_path}/application-secret.conf
ln -s %{herd_etc_path} $RPM_BUILD_ROOT/%{herd_install_path}/conf
ln -s %{herd_repo_path} $RPM_BUILD_ROOT/%{herd_install_path}/repo
ln -s %{herd_uploads_path} $RPM_BUILD_ROOT/%{herd_install_path}/uploads
cp debian/logrotate $RPM_BUILD_ROOT/etc/logrotate.d/%{name}

%clean
rm -rf $RPM_BUILD_ROOT


%files
%defattr(-,root,root,-)
%doc
%config(noreplace) %{herd_etc_path}
%config(noreplace) /etc/default/%name
%{herd_install_path}
%{herd_log_path}
%{herd_lib_path}
%{herd_run_path}
/etc/logrotate.d/%name
%attr(0755, root, root) /etc/init.d/%name

%post
random () {
 i=0
 while [ $i -lt 64 ]
 do
     RAND=$(od -An -N1 -t u1 /dev/urandom)
     val=$(( ($RAND % 62) + 1 ))
     key=$(echo "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" | cut -c $val)
     echo -n $key
     i=$(( i+1 ))
 done
 echo
}

set -e

# Remove tmp contents before upgrading
rm -rf %{herd_lib_path}/tmp/*

if test \! -f %{herd_etc_path}/application-secret.conf
then
    echo Generating new secret key in %{herd_etc_path}/application-secret.conf
    echo -n "application.secret=" > %{herd_etc_path}/application-secret.conf
    random >> %{herd_etc_path}/application-secret.conf
fi

if grep %{herd_user} /etc/passwd > /dev/null
then
	echo User %{herd_user} already exists
else
	echo Creating user %{herd_user}
	adduser --system --home %{herd_install_path} --shell /bin/false --no-create-home \
		--user-group --password \! %{herd_user}
fi


if test \! -d %{herd_repo_path}; then mkdir -p %{herd_repo_path}; fi
if test \! -d %{herd_uploads_path}; then mkdir -p %{herd_uploads_path}; fi

chown %{herd_user}. %{herd_log_path} %{herd_lib_path} %{herd_run_path} %{herd_repo_path} %{herd_uploads_path}

chkconfig --add %{name}

. /etc/default/%{name}

if test "$IS_PLAY_CONFIGURED" = 1
then
	/etc/init.d/%{name} restart
else
	echo "Not starting %{name}, configure %{herd_etc_path}/application.conf and /etc/default/%{name}, then run /etc/init.d/%{name} start"
fi

%changelog

