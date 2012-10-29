#!/usr/bin/env perl
# -*- mode: Perl; tab-width: 4 -*-

use strict;
use warnings;

our $VERSION = 0.1;

use 5.010;
use utf8;
use Carp;
use Readonly;
use IO::Socket::INET;
use POSIX q{setsid};
use Getopt::Std;
use Sys::Syslog qw{:standard :macros};
use Perl::Tidy;
use Time::localtime;
use File::Temp qw/tempfile tempdir/;
use IPC::Cmd qw/run/;

use LWP 5.83;

use Data::Dumper;
use AnyEvent;
use AnyEvent::Strict;
use AnyEvent::XMPP::Client;
use AnyEvent::XMPP::Ext::Disco;
use AnyEvent::XMPP::Ext::MUC;
use AnyEvent::Socket;
use AnyEvent::Handle;

use HTTP::Date;
use HTTP::Message;
use HTTP::Request;
use HTTP::Response;
use HTTP::Status qw/:constants :is status_message/;
use JSON::XS;
use GD::Simple;

Readonly::Scalar my $TMPDIR => tempdir(CLEANUP => 1);
chdir $TMPDIR;

Readonly::Scalar my $DEV_MODE => 0;

Readonly::Scalar my $URL     => 'http://api.twitter.com/1/statuses/update.json';
Readonly::Scalar my $TIMEOUT => 5;
Readonly::Scalar my $USERNAME          => 'entropiap';
Readonly::Scalar my $PASSWORD          => 'XXXXXXXX';             # SECRET
Readonly::Scalar my $SERVER            => 'api.twitter.com:80';
Readonly::Scalar my $REALM             => 'Twitter API';
Readonly::Scalar my $IDENT             => q{};
Readonly::Scalar my $LOGOPT            => q{};
Readonly::Scalar my $FACILITY          => 'LOG_DAEMON';
Readonly::Scalar my $FAILURE_SLEEPTIME => 60;
Readonly::Scalar my $SLEEPTIME         => 1;

Readonly::Scalar my $MPD_AVAILABLE => 1;

Readonly::Scalar my $XMPP_JID      => 'entropiap@stressinduktion.org';
Readonly::Scalar my $XMPP_DEV_JID  => 'entropiap2@stressinduktion.org';
Readonly::Scalar my $XMPP_PASSWORD => 'XXXXXXX';                       # SECRET
Readonly::Scalar my $XMPP_HOST     => 'stressinduktion.org';
Readonly::Scalar my $XMPP_PORT     => 5222;
Readonly::Scalar my $XMPP_MUC     => 'entropia@conference.stressinduktion.org';
Readonly::Scalar my $XMPP_DEV_MUC => 'entropiap@conference.stressinduktion.org';
Readonly::Scalar my $XMPP_RESOURCE => 'clubstatus';

Readonly::Scalar my $HTTP_PORT => 8080;

Readonly::Scalar my $GREEN  => get_circle('green');
Readonly::Scalar my $RED    => get_circle('red');
Readonly::Scalar my $YELLOW => get_circle('yellow');

Readonly::Scalar my $RAINTROPIA =>
 	'/usr/bin/python /home/entropia/raintropia/raintropia.py';


$Carp::Verbose = 1 if $DEV_MODE;

my ( $socket, $ua, $loop, $xmpp, %options, $muc, $temp, $raintropia_timer );

my $generation     = generation();
my $last_event     = AE::time;
my $last_win_state = q{!};
my $last_state     = q{!};
my $weather_data   = -1.0;

local $SIG{INT}  = \&sshutdown;
local $SIG{QUIT} = \&sshutdown;
local $SIG{TERM} = \&sshutdown;
local $SIG{HUP}  = \&sshutdown;

sub slog {
    my ( $prio, $msg ) = @_;
    if ( exists $options{d} ) {
        syslog( $prio, '%s', $msg );
    }
    else {
        printf "%d: %s\n", $prio, $msg;
    }
    return;
}

sub get_circle {
    my ($color) = @_;
    state $img = GD::Simple->new( 14, 14 );
    $img->bgcolor('transparent');
    $img->fgcolor('transparent');
    $img->clear;
    $img->moveTo( 7, 7 );
    $img->bgcolor($color);
    $img->fgcolor($color);
    $img->setThickness(1);
    $img->arc( 13, 13, 0, 360 );
    return $img->png;
}
sub do_effect {
	my ($effect, $times) = @_;

	$times = 1 unless defined $times;

        eval {
                my $snd = IO::Socket::INET->new(
                        PeerAddr => '192.168.23.42',
                        PeerPort => 23421,
                        Proto => 'udp');
                print $snd "$effect $times\n";
                close($snd);
        };
}


sub twitter_update {
    my ($msg) = @_;

    my $marker = ctime();
    slog LOG_INFO, "updating twitter with status: $marker - $msg";

    unless ($DEV_MODE) {
        my $response;
        eval {
            $ua->post( $URL, { status => "$marker - $msg" } );
            1;
        } or do {
            if ( $@ or not( defined $response and $response->is_success ) ) {
                confess $response->status_line . ': '
                  . ( defined $@ ? $@ : q{} );
            }
        };
    }
    slog LOG_INFO, 'finished updating twitter';
    return;
}

sub change_status {
    my ( $new_state, $win_state ) = @_;
    state $winwarn_pending = 0;
    my $warn = q{};

    if (    $new_state eq '1'
        and $last_state eq '0' )
    {
	do_effect("startup");
        `mpc play >/dev/null 2>&1` if ($MPD_AVAILABLE);
        `mpc -p 6601 play >/dev/null 2>&1` if ($MPD_AVAILABLE);

        # club wird betreten
        alarm_disable();
        if ($winwarn_pending) {
            xmpp_alarm('Fenster-Entwarnung');
            $warn            = '; Fenster-Entwarnung';
            $winwarn_pending = 0;
        }
        twitter_update( $new_state . $warn );
    }
    elsif ( $new_state eq '0'
        and $last_state eq '1' )
    {
	do_effect("shutdown");
        `mpc pause >/dev/null 2>&1` if ($MPD_AVAILABLE);
        `mpc -p 6601 pause >/dev/null 2>&1` if ($MPD_AVAILABLE);

        # club wird verlassen
        if ( $win_state eq '1' ) {
            alarm_enable();
            xmpp_alarm('Fenster offen und keiner mehr da!');
            $warn            = '; Achtung: Fenster offen!';
            $winwarn_pending = 1;
        }
        twitter_update( $new_state . $warn );
    }
    elsif ( $new_state eq '1' ) {

        # fenster haben sich geaendert aber Club ist eh offen
    }
    elsif ( $last_win_state eq '1' and $win_state eq '0' ) {

        # fenster geschlossen ohne club-status-aenderung
        if ($winwarn_pending) {
            xmpp_alarm('Fenster-Entwarnung');
            $warn = '; Fenster-Entwarnung';
            twitter_update( $new_state . $warn );
            $winwarn_pending = 0;
        }
        alarm_disable();
    }
    elsif ( $last_win_state eq '0' and $win_state eq '1' ) {
        my $msg = 'Boese Geister im Club - Fenster sind offen!';
        alarm_enable();
        xmpp_alarm($msg);
        twitter_update($msg);
        $winwarn_pending = 1;
    }

    if( $win_state eq '1' )
    {
        set_bin_port( 2, 1);
        set_bin_port( 3, 0);
    } elsif ( $new_state eq '1' ) {
        set_bin_port( 2, 0);
        set_bin_port( 3, 1);
    }
    else {
        set_bin_port( 2, 1);
        set_bin_port( 3, 1);
    }

    slog LOG_WARNING, "error updating led: $@" if ($@);
    return;
}

sub salarm {
    my $stat = shift;
    eval {
        set_bin_port( 1, $stat );
        1;
    } or do {
        slog LOG_ERR, "error setting port: $@" if ($@);
    };
    return;
}

sub alarm_enable {
    salarm(1);
    return;
}

sub alarm_disable {
    salarm(0);
    return;
}

sub xmpp_status {
    my ($status) = @_;
    return unless defined $xmpp;
    eval {
        $xmpp->set_presence( $status, undef, undef );
        return unless defined $muc;
        my $msg = $muc->make_message;
        if ( not defined $status ) {
            $xmpp->set_presence( $status, 'Club offen', 0 );
            $msg->add_body('Club wurde geoeffnet.');
            $msg->send;
        }
        elsif ( $status eq 'away' ) {
            $xmpp->set_presence( $status, 'Club geschlossen', 0 );
            $msg->add_body('Club wurde geschlossen.');
            $msg->send;
        }
        elsif ( $status eq 'na' ) {
            $xmpp->set_presence( $status, 'Hardwarefehler!', 0 );
            $msg->add_body('Hardwarefehler!');
            $msg->send;
        }
        else {
            $xmpp->set_presence( $status, undef, undef );
        }
        1;
    } or do {
        slog LOG_WARNING, 'error updating jabber status';
        xmpp_disconnect();
    };
    return;
}

sub xmpp_alarm {
    my ($body) = @_;
    return unless defined $xmpp;
    eval {
        for my $account ( $xmpp->get_connected_accounts )
        {
            my $roster = $account->connection()->get_roster();
            for my $contact ( $roster->get_contacts ) {
		# my $contact_presence = $contact->get_priority_presence;
		# next if $contact_presence->show ne '';
                my $msg = $contact->make_message;
                $msg->type('chat');
                $msg->add_body($body);
                $msg->send;
            }
        }
        return unless defined $muc;
        my $muc_msg = $muc->make_message;
        $muc_msg->add_body($body);
        $muc_msg->send;
    } or do {
        slog LOG_WARNING, 'error sending alarms over jabber';
        xmpp_disconnect();
    };
    return;
}

sub get_all_contacts {
    my @contacts;
    for my $account ( $xmpp->get_connected_accounts ) {
        my $roster = $account->connection()->get_roster();
        for my $contact ( $roster->get_contacts ) {
            push @contacts, $contact->jid;
        }
    }
    return @contacts;
}

sub cleanup_roster {
    return unless defined $xmpp;
    for my $account ( $xmpp->get_connected_accounts ) {
        my $roster = $account->connection()->get_roster();
        for my $contact ( $roster->get_contacts ) {
            if ( $contact->subscription eq 'from' ) {
                $contact->send_subscribe;
            }
            elsif ( $contact->subscription eq 'to' ) {
                $contact->send_subscribed;
            }
        }
    }
    return;
}

sub get_bin_port {
    my ($portnum) = @_;
    local $/ = "\r\n";
    print {$socket} "GETPORT $portnum\r\n" or confess "socket write error ($!)";
    my $port = <$socket>;
    confess "socket read error ($!)" unless defined $port;
    chomp $port;
    confess "received garbage: $port" if $port !~ m/^0|1$/smx;
    return $port;
}

sub get_temp_port {
	local $/ = "\r\n";
	print {$socket} "GETADC 1\r\n" or confess "socket write error ($!)";
	my $port = <$socket>;
	confess "socket read error ($!)" unless defined $port;
	chomp $port;
	confess "received garbage: $port" if $port !~ m/^\d+$/smx;
	# $temp = $port
	$temp = -19.70488*log(1/(0.0048828*$port) - 0.2) - 56.79574;
}

sub set_bin_port {
    my ( $port, $val ) = @_;
    local $/ = "\r\n";
    confess 'got garbage' if $val !~ m/^0|1$/smx;
    print {$socket} "SETPORT $port.$val\r\n"
      or confess "socket write error ($!)";
    my $ret = <$socket>;
    chomp $ret;
    confess "received garbage: $ret" unless defined $ret and $ret eq 'ACK';
    return;
}

sub check_status {
    my $port1 = get_bin_port(1);
    my $port2 = get_bin_port(2);

    event( ( $port1 == 0 ) ? 1 : 0, $port2 );
    return;
}

sub event {
    my ( $state, $win_state ) = @_;
    $win_state //= 0;

    # check if something toggled
    if ( $last_win_state ne $win_state or $last_state ne $state ) {
        $generation = generation();
        $last_event = AE::time;
        change_status( $state, $win_state );
    }
    $last_win_state = $win_state;
    $last_state     = $state;
    return;
}

sub check_socket_alive {
    if ( not defined $socket ) {
        $socket = IO::Socket::INET->new(
            PeerAddr => '192.168.26.243',
            PeerPort => 50_290,
            Proto    => 'tcp',
            Timeout  => $TIMEOUT
        ) or confess "could not connect to club-status-listener ($!)";

        {
            my $timeval = pack 'L!L!', $TIMEOUT, 0;
            $socket->sockopt( SO_SNDTIMEO, $timeval )
              or confess "setsockopt ($!)";
            $socket->sockopt( SO_RCVTIMEO, $timeval )
              or confess "setsockopt ($!)";
        }
    }
    return;
}

sub daemonize {
    use autodie;
    chdir $TMPDIR;
    open STDIN,  '<', '/dev/null';
    open STDOUT, '>', '/dev/null';
    my $pid = fork;
    exit if $pid;
    setsid or confess "setsid failed ($!)";
    local $SIG{'HUP'} = 'IGNORE';
    $pid = fork;
    exit if $pid;
    open STDERR, '>&', 'STDOUT';
    return;
}

sub check_xmpp_alive {
    state $last_reconnect = 0;
    unless ( defined $xmpp and defined $muc ) {
        if ( $last_reconnect + 10 > int AE::now ) {    # wait 10 AE seconds
            slog LOG_NOTICE, 'jabber connection pending';
            return;
        }
        slog LOG_WARNING, 'reconnecting jabber';
        xmpp_disconnect();
        undef $muc;
        undef $xmpp;
        xmpp_connect( \&xmpp_setup );
        $last_reconnect = int AE::now;
    }
    else {
        state $last_xmpp_state = q{-};
        if ( $last_xmpp_state ne $last_state ) {
            xmpp_status(undef)  if ( $last_state eq '1' );
            xmpp_status('away') if ( $last_state eq '0' );
            xmpp_status('na')
              if ( $last_state eq q{*} or $last_state eq q{!} );
            $last_xmpp_state = $last_state;
        }
    }
    return;
}

sub raintropia {
	my $cmd = $RAINTROPIA;
	eval {
		my @result =
			run( command => $cmd, timeout => 10);
		if ($result[0]) {
			my $wd = $result[3]->[0];
			chomp $wd;
			$weather_data = $wd + 0;
		} else {
			$weather_data = -1.0;
		}
		1;
	} or do {
		$weather_data = -1.0;
	};
	return $weather_data;
}

sub maincheck {
    eval {
        check_xmpp_alive;
        check_socket_alive;
        check_status;
	get_temp_port;

        $loop = AnyEvent->timer(
            after => $SLEEPTIME,
            cb    => \&maincheck
        );

        1;
    } or do {
        slog( LOG_WARNING, "exception caught: $@" );
        close $socket if defined $socket;
        undef $socket;
        eval {
            event q{*};
            xmpp_status('na');
            xmpp_alarm('Hardwarefehler!');
            1;
        } or do {
            slog( LOG_WARNING, "exception caught error event: $@" ) if ($@);
        };
        $loop = AnyEvent->timer(
            after => $FAILURE_SLEEPTIME,
            cb    => \&maincheck
        );
    };
    return;
}

sub xmpp_disconnect {
    return unless defined $xmpp;
    eval {
        $xmpp->disconnect;
        1;
    } or do {
        slog LOG_WARNING, "error disconnecting jabber accounts";
        slog LOG_WARNING, $@ if $@;
    };
    undef $muc;
    undef $xmpp;
    return;
}

sub sshutdown {
    chdir q{/};
    slog LOG_WARNING, 'shutting down clubstatus.pl';
    closelog;
    if ( defined $socket ) {
        close $socket or confess "error closing socket ($!)";
    }
    xmpp_disconnect;
    exit;
}

sub request_subscribe {
    my ( $client, $acc, $roster, $contact, $message ) = @_;
    eval {
        $contact->send_subscribed;
        $contact->send_subscribe;
        slog LOG_INFO, $contact->jid . ' subscribed';
        1;
    } or do {
        slog LOG_WARNING, "error while subscribing contact " . $contact->jid;
        slog LOG_WARNING, $@ if $@;
    };
    return;
}

sub request_unsubscribe {
    my ( $cient, $acc, $roster, $contact, $message ) = @_;
    eval {
	$contact->send_unsubscribed;
	$contact->send_unsubscribe;
	slog LOG_INFO, $contact->jid . ' unsubscribed';
    } or do {
	slog LOG_WARNING, "error while unsubscribing contact " . $contact->jid;
	slog LOG_WARNING, $@ if $@;
    };
}

sub xmpp_connect {
    my ($cb) = @_;
    $xmpp = AnyEvent::XMPP::Client->new( debug => 0 );
    $xmpp->add_account(
        ($DEV_MODE) ? $XMPP_DEV_JID : $XMPP_JID,
        $XMPP_PASSWORD,
        $XMPP_HOST,
        $XMPP_PORT,
        {
            resource     => $XMPP_RESOURCE,
            disable_sasl => 1
        }
    ) or confess 'error setting up account';
    {
        $xmpp->reg_cb(
            connected                 => $cb,
            contact_request_subscribe => \&request_subscribe,
            error => sub { slog LOG_WARNING, $_[2]->string() },
            connect_error => sub { undef $muc; undef $xmpp; },
	    contact_unsubscribed => sub { $_[3]->send_unsubscribed(); },
	    contact_did_unsubscribe => sub { $_[3]->send_unsubscribe(); }
        );
        $xmpp->start();
    }
    return;
}

sub xmpp_setup {
    my @contacts = get_all_contacts;
    {
        local $" = ', ';
        slog LOG_INFO, "subscribers: @contacts";
    }
    cleanup_roster;

  MUC_JOIN: for my $account ( $xmpp->get_accounts ) {
        eval {
            my $con = $account->connection;
            $con->add_extension( my $disco = AnyEvent::XMPP::Ext::Disco->new );
            $con->add_extension( my $entropia_muc =
                  AnyEvent::XMPP::Ext::MUC->new( disco => $disco ) );
            $entropia_muc->join_room( $con,
                ( $DEV_MODE == 1 ) ? $XMPP_DEV_MUC : $XMPP_MUC, 'entropiap' );
            $entropia_muc->reg_cb(
                enter => sub {
                    $muc =
                      $entropia_muc->get_room( $con,
                        ( $DEV_MODE == 1 ) ? $XMPP_DEV_MUC : $XMPP_MUC );
                }
            );
            1;
        } or do {
            slog LOG_WARNING, "error connecting to jabber ($@)" if $@;
            next MUC_JOIN;
        };
        last MUC_JOIN;
    }
    return;
}

sub http_get {
    my ( $code, $request, $content_type, $content ) = @_;
    my $response = HTTP::Response->new($code);
    $response->protocol( $request->protocol() );
    $response->request($request);
    $response->date(time);
    $response->header(
        'Server'           => 'Clubstatus',
        'Content-Length'   => bytes::length($content),
        'Content-Type'     => $content_type,
        'Content-Encoding' => 'identity',
        'Cache-Control'    => 'no-store, no-cache',
        'Expires'          => time2str,
        'Connection'       => 'close'
    );
    $response->content_ref( \$content );

    return $response;
}

sub http_json_data {
    use bigint;
    my ($request) = @_;
    my $coder     = JSON::XS->new->utf8->pretty->indent;
    my $json_data = $coder->encode(
        {
              club_offen => ( $last_state eq '1' ) ? JSON::XS::true
            : JSON::XS::false,
            fenster_offen => ( $last_win_state eq '1' ) ? JSON::XS::true
            : JSON::XS::false,
            hardware_fehler => ( $last_state eq q{*} ) ? JSON::XS::true
            : JSON::XS::false,
            generation => int $generation,
            last_event => ctime($last_event),
	    temp => $temp,
	    raintropia => $weather_data,
        }
    );
    $json_data .= "\r\n";

    return http_get( 200, $request, 'application/json; charset=utf-8',
        $json_data );
}

sub http_png_data {
    my ($request) = @_;
    my $color;
    if ( $last_state eq '1' ) {
        $color = \$GREEN;
    }
    elsif ( $last_state eq '0' ) {
        $color = \$RED;
    }
    else {
        $color = \$YELLOW;
    }
    return http_get( 200, $request, 'image/png', ${$color} );
}

sub generation {
    use bigint;
    return int rand( 2**63 - 1 );    # be compatible with java longs
}

$ua = LWP::UserAgent->new;
$ua->agent('Clubstatus');
$ua->timeout(10);
$ua->credentials( $SERVER, $REALM, $USERNAME, $PASSWORD );

getopts( 'd', \%options ) or confess 'illegal command line switch';
if ( exists $options{d} ) {
    daemonize;
    openlog $IDENT, $LOGOPT, $FACILITY;
    slog LOG_INFO, 'starting up clubstatus';
}

my %http_produce = (
    q{/}           => \&http_json_data,
    '/status.png'  => \&http_png_data,
    '/status.json' => \&http_json_data
);

tcp_server undef, $HTTP_PORT, sub {
    my ( $fh, $host, $port ) = @_;
    my $handle;
    eval {
       # slog LOG_INFO, "http-connection from $host:$port";
        $handle = AnyEvent::Handle->new(
            fh       => $fh,
            on_error => sub {
                $handle->destroy;
            }
        );
        $handle->push_read(
            line    => "\r\n\r\n",
            timeout => 60,
            sub {
                my ( $h, $line ) = @_;

                eval {
                    $line .= "\r\n\r\n";    # reattach http end

                    my $request = HTTP::Request->parse($line);
                    if (    defined $request
                        and $request->method eq 'GET'
                        and defined $request->header('host') )
                    {
                        my $func = $http_produce{ $request->uri->as_string };
                        if ( defined $func
                            and ref($func) eq 'CODE' )
                        {
                            my $response = &{$func}($request);
                            $h->push_write( $response->as_string("\r\n") );
                        }
                        else {
                            my $content  = status_message(404) . "\r\n";
                            my $response = http_get( 404, $request,
                                'text/plain; charset=utf-8', $content );
                            {
                                use bytes;
                                $h->push_write( $response->as_string("\r\n") );
                            }
                        }

                    }
                    else {
                        my $content = status_message(400) . "\r\n";
                        my $response =
                          http_get( 400, $request, 'text/plain; charset=utf-8',
                            $content );
                        $h->push_write( $response->as_string("\r\n") );
                    }
                    $h->push_shutdown;
                    1;
                } or do {
                    slog LOG_WARNING,
                      "i/o error processing http request from $host:$port";
                    slog LOG_WARNING, $@ if $@;
                };
                $h->destroy;
            }
        );
        1;
    } or do {
        slog LOG_WARNING, "error processing http request from $host:$port";
        slog LOG_WARNING, $@ if $@;
    };
};

# update weather data
# raintropia;

$raintropia_timer = AnyEvent->timer(
	after => 0,
	interval => 600,
	cb => \&raintropia
);

$loop = AnyEvent->timer(
    after => 0,
    cb    => \&maincheck
);

AnyEvent->condvar->recv;

chdir q{/};
sshutdown();

