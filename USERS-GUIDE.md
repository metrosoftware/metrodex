----
# USERS GUIDE #

----
## What is in for me with Metro? ##

  - a **decentralized** platform for my assets like money, company shares etc.
  - almost all trusted third parties **removed**
  - many useful financial, economical and social features

----
## What do I need to do? ##

  - for a quick start, see README.md
  - for configuration and updates, see OPERATORS-GUIDE.md

----
## How does it work? ##

### MRS (Metro Reference Software) ###

  - client-server application
  - JavaFX UI on supported platforms
  - also access via a Web browser like Mozilla Firefox or Google Chrome
  - accounts are accessible from everywhere in the world
  - very strong and long passphrases required

### Metro ###

  - peer-to-peer protocol
  - efficient blockchain technology (can run on low-power devices)
  - allows payment *transactions*, *exchange trading*, *name registrations*, *voting* and much more
  - no need to trust third parties anymore

### Forging (aka block generation) ###

  - reward for securing the network
  - start via the browser interface
  - requires only:
    - decent amount of the base currency MTR
    - running MRS (browser or tab can be closed)
    - very little CPU power

## Installation ##
For installing on Windows download latest version from https://github.com/metrosoftware/metrodex/releases .

#### WINDOWS ####
Running: run.bat

#### LINUX, UBUNTU ####
Running: run.sh 


## Mining ###

### Configuring wallet ###
Update conf/metro.properties with your white IP. This is needed for adding you as peer by other nodes.
`metro.myAddress=<your IP>`
Otherwise other peers won't send you blocks as soon as created.

### Cpu mining ###

Clone git-repo from `https://github.com/metrosoftware/metrodex.git`

Create or Update conf/metro.properties. Insert your publicKey (You can find it out in Metro client: press on account balance value in top-left corner. In opened modal window there is field ‘Public Key’)

`metro.mine.publicKey=<your public key>`

Add your Metro node address

`metro.mine.serverAddress=<server_address>` . Default value = localhost. Set different if you want to get work from remote node. 

#### LINUX, UBUNTU ####
Run: ./compile.sh in project root.

Execute: ./mining.sh

#### WINDOWS ####

Execute: miner.bat



### GPU mining ###
Download Metro wallet.
Create or Update conf/metro.properties. Insert your publicKey (You can find it out in Metro client: press on account balance value in top-left corner. In opened modal window there is field ‘Public Key’)
`metro.mine.publicKey=<your public key>`

Add your Metro node address

`metro.mine.serverAddress=<server_address>` . Default value = localhost. Set different if you want to get work from remote node.


#### AMD #### 
Download sgminer from https://github.com/metrosoftware/sgminer/releases
Running: 
For windows:
sgminer --algorithm metro -o http://127.0.0.1:7886/metro?requestType=getWork -u= -p= --intensity d

For linux:
./sgminer --algorithm metro -o http://127.0.0.1:7886/metro?requestType=getWork -u= -p= --intensity d


#### Nvidia #### 
Download ccminer from https://github.com/metrosoftware/ccminer/releases
Install CUDA 9.2 from Nvidia site.

Running: 
For windows:
ccminer -a metro -o http://localhost:7886/metro?requestType=getWork --user= --pass= -i 20

For linux:
./ccminer -a metro -o http://localhost:7886/metro?requestType=getWork --user= --pass= -i 20


WARNING: use only miners with metro algo support. Now it's only our miners. We forked base ccminer, sgminer and add metro algo support. Base ccminer, sgminer don't support metro algo. 

----
## How can I contribute? ##

  - review pull requests
  - help users on issues
  - join the forums and find places where you can help
  - get your friends to join Metro
  - generate blocks via the forging capability
  - ask us, the dev team

----