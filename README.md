# eHealth

This README contains detailed information on eHealth, an information system tailored for hospitals and private clinics to track the status of patients. The system various security systems and access control mechanisms to protect unauthorized users to access critical information regarding patients, therefore preserving their privacy.
After General Information on eHealth, this document specifies the technical details and requirements to deploy, test and use the system.

## General Information

Nowadays, hospitals are complex organisations with various employees: each of them has access to eHealth through a personal account. We assume that the HR department of the hospital is in charge of generating the credentials upon employment through an "ADMIN" account. Employees' categories (that we define as "roles") should be able to access only a subset of the Medical Records of the patients, associated to their specific duties.
Moreover, during extraordinary situations of emergency, there could be the need to dramatically change most of the employees' permissions of access to Medical Records , as it could be required to promptly move personnel to different tasks.
As an example, eHealth includes two exemplary "working modes" (Normal Mode and Pandemic Mode) to simulate the revolution of the personnel organisation experienced during the first wave of the COVID-19 pandemic. The system is able to seamlessly switch operation mode and, with it, to completely change medical records' access control policies, with minimal service downtime.
The system we present involves many components that have been deployed over a secured network to prevent various types of attacks. The architecture supports the presence of an external "Partner Lab" that can provide clinical analysis. The Partner Lab employees have access to the hospital information system, although with strict permissions.
Other attack prevention strategies are implemented at the application level, as well, to guarantee various security properties.

Following, a list of roles to which emoloyees' accounts are associated:
* DOCTOR
* NURSE
* PATIENT_SERVICES_ASSISTANT
* CLINICAL_ASSISTANT
* WARD_CLERK
* LAB_EMPLOYEE

A patient's Medical Record looks as follows:
* _PatientID_
* Name and Surname 
* Personal Data (such as email, home address and health number)
* Health Problems
* Prescribed Medications
* Health History
* Allergies
* Visits History
* Lab Results

The following tables describe the read/write permissions associated to each account type for every resource type included in the Medical Records:

### Normal Mode
| ROLE                        | READ                                       | WRITE                                                    |
|-----------------------------|--------------------------------------------|----------------------------------------------------------|
| Lab Employees               | Lab Results                                | Lab Results                                              |
| Doctors                     | Everything but personal data               | Everything but personal data, visitsHistory, LabResults  |
| Nurses                      | Everything but history and personal data   | Everything but personal data, VisitsHistory, LabResults  |
| Patient Services Assistants | Only personal data and problems            | NOTHING                                                  |
| Clinical Assistants         | Only Personal Data, allergies and problems | NOTHING                                                  |
| porters volunteers          | Only problems                              | NOTHING                                                  |
| ward clerks                 | Everything                                 | Everything                                               |

### Pandemic Mode
| ROLE                        | READ                         | WRITE                                         |
|-----------------------------|------------------------------|-----------------------------------------------|
| Lab Employees               | Everything but personal data | Everything but personal data, visitsHistory   |
| Doctors                     | Everything but personal data | Everything but personal data, visitsHistory   |
| Nurses                      | Everything but personal data | Everything but personal data, visitsHistory   |
| Clinical Assistants         | Everything but personal data | Everything but personal data, visitsHistory   |
| Patient Services Assistants | Only personal data           | NOTHING                                       |
| porters volunteers          | Only Problems                | NOTHING                                       |
| ward clerks                 | Everything                   | Everything                                    |

It is important to note that these policies are invented and can be fully customized.

### Built With

The following list includes the technologies involved in eHealth:

* [Java](https://openjdk.java.net/) - Programming Language and Platform
* [Maven](https://maven.apache.org/) - Build Tool and Dependency Management
* [MySQL](https://www.mysql.com) - Database Engine
* [gRPC](https://grpc.io) - RPC framework
* [XACML3.0](https://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html) - Access Control framework
* [rfc2753](https://datatracker.ietf.org/doc/html/rfc2753) - Policy-based Admission Control Framework
* [Balana](https://github.com/wso2/balana/tree/1a5a594aaf823fb43063fdee02145176276842a3) - XACML Implementation

## Getting Started

After deploying the network, the following tasks are necessary:

* Create the **Database** with: https://gist.github.com/enricoSteez/07da598d13a3bbc28edfa1fc8b111b19
* Start the **PDP** server with `mvn exec:java -Dexec.mainClass="PDPServer" [-Dexec.args="PandemicMode"]` : the default operational mode is "NormalMode", adding "PandemicMode" as an argument will switch the permissions to Pandemic Mode
* Start the **Application Server** with `mvn exec:java -Dexec.mainClass="ServerTls" -Dexec.args="50440 <PDPServer_IP>:8980"`
* Start the **Client** with `mvn exec:java -Dexec.mainClass="Client" -Dexec.args="localhost 50440"`


### Prerequisites

All the machines must be running Linux and have Java installed.
The **Database** and **PEP** machine must have mysql installed

```
#ON EVERY MACHINE
sudo apt update
sudo apt upgrade
sudo apt install maven

#ON THE PEP MACHINE
sudo apt install mysql-server

#ON THE DATABASE MACHINE
sudo apt install mysql-server
sudo mysql_secure_installation
  //Define a strong password
  //Accept every security recommendations
```

### Installing

Give step-by-step instructions on building and running the application on the development environment. 

Describe the step.

### Creating user certificates and keys

generate private key:
  ```openssl genpkey -out 'client_name'.key -algorithm RSA -pkeyopt rsa_keygen_bits:2048```
  
generate csr (certifacte signing request):
  ```openssl req -key 'client_name'.key -new -out 'client_name'.csr```
  
Create 'client_name'.ext file with following contents:
```
authorityKeyIdentifier=keyid,issuer

basicConstraints=CA:FALSE

subjectAltName = @alt_names

[alt_names]

IP.1 = client_ip

DNS.2 = localhost
```

Sign csr with certificate authority:
```openssl x509 -req -CA 'path_to_rootCA.crt' -CAkey 'path_to_rootCA.key' -in 'client_name'.csr -out 'client_name'.crt -days 365 -CAcreateserial -extfile 'client_name'.ext```

edit Client/config.properties file with new certificate and key paths


#### Configuration of the **Database** machine

Create a user
```
sudo mysql
  CREATE USER 'user'@'localhost_or_IP' IDENTIFIED BY 'password';
  GRANT ALL PRIVILEGES ON 'database_name'.* TO 'user'@'localhost_or_IP';
  exit
```

Configure SSL keys
```
#In the file /etc/mysql/my.cnf add with your own *.crt and *.key:
  [mysqld]
  ssl
  ssl-cipher=DHE-RSA-AES256-SHA
  ssl-ca=/mysql_keys/rootCA.crt
  ssl-cert=/mysql_keys/db.crt
  ssl-key=/mysql_keys/db.key
  
  [client]
  ssl-mode=REQUIRED
  ssl-cert=/mysql_keys/server.crt
  ssl-key=/mysql_keys/server.key

#In the folder /etc/mysql/mysql_keys copy the *.crt and *.key files with matching names from folders Keys/ and Keys/DBKeys/

#For new database certificates, create them as with the clients, and add them to a new truststore

keytool -import -alias certificate_authority(root_CA.cer) -file database_certificate.cer -keystore strust_store_name -storepass password [Return]

edit ServerTLS/config.properties file's fields to new trustore path and password

service mysql restart
sudo mysql
  show variables like '%ssl%';    //Check if your changes have been taken into account
  exit
```

Allow the remote connection
```
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
#Change the bind-address to the desired address
```

Import your database script
```
sudo mysql
  CREATE DATABASE 'database_name';
  exit

sudo mysql 'database_name' < 'database_script'.sql
```

#### Configuration of the **Firewall** machines

For the **Firewall** between the **Client** and the **PEP**: find the tcp port used for the communication to allow it on the network interface between the **Firewall** and the **PEP**, and drop all other (white-listing)
```
sudo iptables -P FORWARD DROP
sudo iptables -P INPUT DROP
sudo iptables -P OUTPUT DROP
sudo iptables -A FORWARD -i <network_interface> -p tcp --dport <port> -j ACCEPT
sudo iptables -A FORWARD -i <network_interface> -p tcp --dport <port> -j ACCEPT
sudo iptables -A FORWARD -o <network_interface> -p tcp --sport <port> -j ACCEPT
sudo iptables -A FORWARD -o <network_interface> -p tcp --sport <port> -j ACCEPT
```

For the **Firewall** of the **Client**, you can adapt the configuration according to your preference
```
sudo iptables -P FORWARD DROP
sudo iptables -t nat -A POSTROUTING  -o <external_network_interface> -j MASQUERADE
iptables -A FORWARD -i <internal_network_interface> -p tcp --dport 80 -j ACCEPT
iptables -A FORWARD -i <internal_network_interface> -p tcp --dport 443 -j ACCEPT
iptables -A FORWARD -o <internal_network_interface> -p tcp --sport 80 -j ACCEPT
iptables -A FORWARD -o <internal_network_interface> -p tcp --sport 443 -j ACCEPT
iptables -A FORWARD -i <internal_network_interface> -p tcp --sport 80 -j ACCEPT
iptables -A FORWARD -i <internal_network_interface> -p tcp --sport 443 -j ACCEPT
iptables -A FORWARD -o <internal_network_interface> -p tcp --dport 80 -j ACCEPT
iptables -A FORWARD -o <internal_network_interface> -p tcp --dport 443 -j ACCEPT
```

To make them persistent
```
sudo apt install iptables-persistent
#At the first installation, it will ask to save your rules automatically.

#But for the next saves
#To save
sudo iptables-save > /etc/iptables/rules.v4

#To restore manually
sudo iptables-restore < /etc/iptables/rules.v4


#To run them at the boot, inside /etc/network/interfaces
  iface <network_interface> inet static
    address ...
    netmask ...
    gateway ...
    ...
    pre-up iptables-restore < /etc/network/iptables.rules.v4
```

#### Useful network commands

IP Forwarding
```
#To check
sysctl net.ipv4.ip_forward

#Enable IP forwarding
#Inside /etc/sysctl.conf, uncomment
	net.ipv4.ip_forward=1
sysctl -p
```

Routing
```
#Add a route
sudo ip route add <network_to_reach>/<netmask> via <network_reachable>

#Delete a route
sudo ip route del <network_to_reach>/<netmask> via <network_reachable>

#Make them persistent, inside /etc/network/interfaces
  iface <network_interface> inet static
    address ...
    netmask ...
    ...
    up route add -net <network_to_reach> netmask <netmask_IP_format> gw <network_reachable>
    down route del -net <network_to_reach> netmask <netmask_IP_format> gw <network_reachable>
```

## Additional Information

### Authors

* **Enrico Giorio** - [enricoSteez](https://github.com/enricoSteez)
* **Daniel Correia** - [DanielCorreia21](https://github.com/DanielCorreia21)
* **Luxithan Kailairajan** - [Luxithan](https://github.com/Luxithan)
