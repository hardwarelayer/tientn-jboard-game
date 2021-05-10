## TienTN JBoard Game: War of the words

![image](https://user-images.githubusercontent.com/20723609/117677335-91142180-b1d8-11eb-9859-8c3f8891e5e1.png)

A desktop game with language learning feature, coded in Java using Swing and Docker instances. 

Features of territories buidling and board based, turn based war games.

Since I have played a lot of strategy series, from Civilization and EU4, IronHearts, Victoria ... But they are over complicated

The other games are too simple ...

... so I think it's time to forge a game of my imagination.

---------------

### Notes:

I make a board game based on TripleA, this game will be more 'territory building' than the original.

Other feature: Language learning while playing

I will try to make this game still compatible with the base TripleA, for any updates.

My branch is always on feature-tientn-j-lang

The branch 'master' will always be used to keep up with newer releases of the core TripA releases, except this file.

Good luck with the game :)

### Updates:
Current version: 0.2

Current core release: 2.5.22294

History of core engine updates:

No | Version | Date | By
:-- | :-- | :-- | :--
1 | 2.3.21874 | 2020/10/04 | Tien
2 | 2.5.22294 | 2021/04/08 | Tien

### Porting:

This is my porting of tripleA to MacOSX Catalina, JDK15, Docker 2400(48506), gradle 6.6.1

Changes for porting: build parameters, tool and script environment ...


### Running locally:

    %cd database

    %./start_docker_db

    %docker ps

    %./load_sample_data

    %./connect_to_docker_db

    %cd ..

    %./gradlew clean

    %./gradlew spotlessApply

    %./gradlew lobby-server:run

    %./gradlew game-headed:run

#### Setting game client:

    Go to Engine Reference

    Go to testing

      Change Lobby URI Override: -> Localserver (http://localhost:8080)

      Save and close

    Rerun game headed again

#### To update with newer release:

    1. make a new branch from master: core-release-xxxxx

    2. manually copy new release code ONTO this new branch

    3. ignore Readme.MD and check build.gradle (because we made change to compile)

    4. PR core-release-xxxxxx to master

    5. merge master to feature-tientn-j-lang (cannot be rebased)

    6. rebuild and run

    7. commit to feature-tientn-j-lang

    8. update master Readme.MD with release info


### Example of updating with 2.5.22294

make a tar of tripleA release source code without the folder (so we can extract and overwrite directly to our source)

    % cd place_of_download

    % tar xvf triplea-2.5.22294.tar.gz

    % cd triplea-2.5.22294

    % tar cvf ../triplea-root-2.5.22294.tar *

    % cd ..

    % rm -rf triplea-2.5.22294

    % cd tientn-jboard-game

    % git checkout master

    % git pull

    % git checkout -b core-release-2.5.22294

    % tar xvf ../place_of_download/triplea-root-2.5.22294.tar

    % git add .

    % git commit -m "upgrade to new version 2.5.22294"

    % git push

Create a PR to master on github.com/hardwarelayer/tientn-jboard-game/core-release-2.5.22294

    merge it to master

    delete the branch core-release-2.5.22294

VSCode clone:

    Command+Shift+P: "Git: Clone", Open it

    Choose Source Control tab on the left

    Click "..." Choose checkout to: feature-tientn-j-lang

    Click "..." Choose Checkout to ... master

    Conflicted files are shown in Merge Changes list on top left

Support merging side by side VS

    Create two folder of master and feature-tientn-j-lang to compare source by TC / DC

        Show differents for helping changes in VS

In VS choose Accept options in conflicted blocks

    Choose Stage conflicted files

Lost file, must recover after merge

cp ../backup-working-before-merge/tientn-jboard-game/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties

Rebuild : ./run.sh

List of file which need auto-merging and conflicts:

    Auto-merging game-headed/build.gradle
    
    CONFLICT (content): Merge conflict in game-headed/build.gradle
    
    Auto-merging game-core/src/main/java/games/strategy/triplea/ui/TripleAFrame.java
    
    Auto-merging game-core/src/main/java/games/strategy/triplea/delegate/battle/steps/change/CheckStalemateBattleEnd.java
    
    Auto-merging game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorPanel.java
    
    CONFLICT (content): Merge conflict in game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorPanel.java

    Auto-merging game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorModel.java

    CONFLICT (content): Merge conflict in game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorModel.java

    Auto-merging game-core/src/main/java/games/strategy/engine/framework/startup/mc/ServerModel.java

    Auto-merging game-core/src/main/java/games/strategy/engine/framework/save/game/GameDataWriter.java

    Auto-merging game-core/src/main/java/games/strategy/engine/framework/GameDataManager.java

    CONFLICT (content): Merge conflict in game-core/src/main/java/games/strategy/engine/framework/GameDataManager.java

    Auto-merging game-core/src/main/java/games/strategy/engine/data/GameData.java

    Auto-merging build.gradle

    Auto-merging README.md

    CONFLICT (content): Merge conflict in README.md

    Automatic merge failed; fix conflicts and then commit the result.

% git status

Unmerged paths:

    both modified:   README.md
    
	both modified:   game-core/src/main/java/games/strategy/engine/framework/GameDataManager.java

    both modified:   game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorModel.java
	
    both modified:   game-core/src/main/java/games/strategy/engine/framework/startup/ui/panels/main/game/selector/GameSelectorPanel.java
	
    both modified:   game-headed/build.gradle

Close VS

    % git add .

    % git commit -m "merged master - engine 2.3 to 2.5.22294 on this branch"

    % git push

Done

Still keep the folder of engine 2.3 before changed in:

    JBoard/bk.tientn-jboard-game.tar.gz

    JBoard/backup-working-before-merge

### TODO: Rebasing (not success yet):

    % git checkout feature-tientn-j-lang

    % git clean -df (delete untracked files to avoid conflict-backup first!!!)

    % ./gradlew clean (clean build files - *.class ... etc.)

    % ps aux | grep gradle <-- it keep .bin and .lock files in folders

    % kill -9 <pid>

    % find ./.gradle -type f -name "*.lock" -delete

    % find ./.gradle -type f -name "*.bin" -delete

    % find ./.gradle -type f -name "gc.properties" -delete

    % find ./.gradle -type f -name "cache.properties" -delete

    % git rebase master (not success yet)

### Add ignore:

    % vi .gitignore         

    % git config --global core.excludesFile ~/.gitignore

### Run standalone game locally:

    1. Not need docker or db

    2. use ./run.sh

    ####Note: current java version

        java version "15" 2020-09-15

        Java(TM) SE Runtime Environment (build 15+36-1562)

        Java HotSpot(TM) 64-Bit Server VM (build 15+36-1562, mixed mode, sharing)

*** Changes and TODOs: ***

    2020-10-04: load tooltip.properties with UTF-8

    Other changes see commits (huge)

*** From 2021-04-08 ***

    1. bug: validate word ok, but remove selected item, so if we choose an item and move list selected to another, that item will be removed if all selected words are matched -> Should remove selected words not list selected items

    [Fixed] 2. bug: can't build news if human are the first player in list

    [Done] 3. improve: build list of word by order, but each time, should include one or two random words from next steps (always same items each time is boring)

    [Done] 4. improve: options to load newer word items (normal load is word with correct < 10, new load < 3)

    [Done] 5. improve: Select word move to next list, double ENTER on last list with same selected word equals to SHIFT+ENTER

    [Done] 6. improve: bigger and better font size for WordMatch game

    [Done] 7. improve: remove default buttons(OK, cancel ... ) from our game dialog and territory dialog

    [Done] 8. improve: add newspaper icon to territory canvas, remove Newspaper button from Territory dialog

    [Done] 9. improve: add key shortcut for the WordMatch panel

    [Done] 10. improve: change kanji item logic, so if a word is reached min correct limit, but got wrong answer after that, the correct count will be reduced, thus can bring the word to test more frequently.

    [Done] 11. improve: show statistics on WordMatch: total learned words / total words, avg tests / learned word, needed tests 

    [Fixed] 12. bug: after WordMatch game, back to territory canvas and build dialog, new JCoin has not updated, must close Territory and reopen

    13. improve: after a while, we'll have a lot of jCoin each turn, need a mechanism to spend these jCoin, for example: maintenance cost of buildings, if not enough, buildings may disappear ... so we have to earn jCoin on every turn

    [Done] 14. improve: focus back to first word list after complete one test

------------------

## License

This project is licensed under the terms of the
[GNU General Public License v3.0 with additional permissions](/LICENSE).

Copyright (C) 2001-2019 TripleA contributors.

Part of tientn-jboard-game is (c) by Tran Ngoc Tien.

This program is free software; you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License below for more details.

#### Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with any
of the following libraries (or a modified version of those libraries), containing
parts covered by the terms of the library's associated license, the licensors of
this Program grant you additional permission to convey the resulting work.

Library | Group ID | Artifact ID | SPDX License ID
:-- | :-- | :-- | :--
Jakarta Mail | com.sun.mail | jakarta.mail | GPL-2.0-only
