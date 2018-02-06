// ==================================================================================================
// Setup
// ==================================================================================================

import groovy.json.JsonSlurperClassic
def payload = new JsonSlurperClassic().parseText(env.payload)

println "BUILD_ID IS: " +env.BUILD_ID
println "\nJSON PAYLOAD : "+env.payload

def branchName = payload.ref
def projectName = payload.repository.name
def artifactName = "${projectName}"
def deployBase = "/appvol/applications"
def localArtifactPath = "/var/jenkins_home/${projectName}/${branchName}/${env.BUILD_ID}"
def nexusRepo = "https://blah/repo/repository/wap-raw-repo/"
def nexusUser = "user"
def nexusPass = "pass"
def userName = "my_user"
def sonarSource = "src"
def targetServer
def port = "8080"

def reportHost = "my_host1"
def serverMap = [
    'stage1':'my_host2',
    'stage2':'my_host3',
    'stage3':'my_host4'
]

// ==================================================================================================
// Main program
// ==================================================================================================


println "PROJECT NAME: "+projectName
println "BRANCH NAME: "+branchName

def eventType = getEventType( payload )
switch (eventType) {
   case "push":
        def repository = payload.repository.name
        def pusher = payload.pusher.name
        def mergeToBranch = "not-set"
      def commitMessage = payload.head_commit.message

        // Run full set of tests when Jenkinsfile change is submitted - for testing - to be removed
        if( repository.toLowerCase().contains('net-pilot-ui-settings') ){
            if( commitMessage.contains( 'TRIGGER')) {
                println("This is a push for a Jenkinsfile change, running full pipeline")
                currentBuild.displayName = "Jenkinsfile change"
            projectName = 'net-pilot-ui'
            def id = payload.head_commit.id.substring(0, 8)
                def s = branchName.minus("refs/heads/")

                cloneRepo('dev');
                unitTests(reportHost, projectName);
                lcovTests(reportHost, projectName);
                sonarTest(projectName, branchName, reportHost, projectName, sonarSource);
                npmInstall();
                uploadArtifact(localArtifactPath, artifactName + ".mock", nexusRepo, nexusUser, nexusPass, ".", "Build mocked artifact", "npm run buildMockProd");
                uploadArtifact(localArtifactPath, artifactName + ".live", nexusRepo, nexusUser, nexusPass, ".", "Build live artifact", "npm run build");


                // CD stage 1
                targetServer = serverMap.get('stage1')
                deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage1 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
                mockCdn(projectName, targetServer, userName, deployBase, "CD-Stage1")
                //runFeatureTests("CD-Stage1", reportHost)

                // CD stage 2
                targetServer = serverMap.get('stage2')
                deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage2 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
                mockCdn(projectName, targetServer, userName, deployBase, "CD-Stage2")
                //runCitTests("CD-Stage2")

                // CD stage 3
                targetServer = serverMap.get('stage3')
                deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage3 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
                mockCdn(projectName, targetServer, userName, deployBase, "CD-Stage3")
                //runJourneyTests("CD-Stage3")

                def tag = "test-tag-please-ignore"
                println( "Tagging artifacts");
                tagArtifact( env.BUILD_ID + "_mock", projectName, localArtifactPath, artifactName + ".mock", nexusRepo, nexusUser, nexusPass, tag, "Publish mocked artifact" )
                tagArtifact( env.BUILD_ID + "_live", projectName, localArtifactPath, artifactName + ".live", nexusRepo, nexusUser, nexusPass, tag, "Publish live artifact" )
                break;
            }
            else {
                println("This is a push for a Jenkinsfile change however the pipeline had not been triggered")
                currentBuild.displayName = "Jenkinsfile change"
                def buildNumber = env.BUILD_ID
                node {
                    sh "curl -X POST http://trigger:4615faafef698c25100208969391c9d6@jupiter.stack1.com:8080/job/execute-pipeline/${buildNumber}/doDelete"
                }
            }
        }

        else {
            println( "This is a push, running static analysis" );
            def branch = branchName.minus( "refs/heads/" )
            def id = payload.head_commit.id.substring(0,8)
            currentBuild.displayName = "push: >> ${branch} ${id}"
            cloneRepo( branch );
            unitTests(reportHost, projectName);
            lcovTests(reportHost, projectName);
            sonarTest( projectName, branchName, reportHost, projectName, sonarSource);
            npmInstall();
        }
        break;

   case "pull_req":
      println( "This is a pull_request, running full pipeline" )
      def mergeFromBranch = payload.pull_request.head.ref
      def mergeToBranch = payload.pull_request.base.ref
      def sha = payload.pull_request.head.sha.substring(0,8)
      def tag = payload.ref
      def requester = payload.pull_request.user.login
      currentBuild.displayName = "pull_req: ${mergeFromBranch} >> ${mergeToBranch} ${sha}"
      cloneRepo( mergeFromBranch );
      unitTests(reportHost, projectName);
      lcovTests(reportHost, projectName);
      sonarTest( projectName, branchName, reportHost, projectName, sonarSource);
      npmInstall();

        uploadArtifact(localArtifactPath, artifactName + ".mock", nexusRepo, nexusUser, nexusPass, ".", "Build mocked artifact", "npm run buildMockProd");
        uploadArtifact(localArtifactPath, artifactName + ".live", nexusRepo, nexusUser, nexusPass, ".", "Build live artifact", "npm run build");

      // CD stage 1
      targetServer = serverMap.get('stage1')
      deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage1 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
      mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage1" )
      //runFeatureTests("CD-Stage1", reportHost)

      // CD stage 2
      targetServer = serverMap.get('stage2')
      deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage2 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
      mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage2" )
      //runCitTests("CD-Stage2")

      // CD stage 3
      targetServer = serverMap.get('stage3')
      deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage3 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
      mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage3" )
      //runJourneyTests("CD-Stage3")
      break;

    case "tag":
        println( "This is a tag create, running full pipeline and publishing artifact" )
        def tag = payload.ref.split('/')[2]
        def mergeToBranch = payload.base_ref.split('/')[2]
        currentBuild.displayName = "tag: ${tag} >> ${mergeToBranch} "
        cloneRepo( mergeToBranch );
        unitTests(reportHost, projectName);
        lcovTests(reportHost, projectName);
        sonarTest( projectName, branchName, reportHost, projectName, sonarSource);
        npmInstall();
        //buildMockProd()
      uploadArtifact(localArtifactPath, artifactName + ".mock", nexusRepo, nexusUser, nexusPass, ".", "Build mocked artifact", "npm run buildMockProd");
        uploadArtifact(localArtifactPath, artifactName + ".live", nexusRepo, nexusUser, nexusPass, ".", "Build live artifact", "npm run build");

        // CD stage 1
        targetServer = serverMap.get('stage1')
        deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage1 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
        mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage1" )
        //runFeatureTests("CD-Stage1", reportHost)

        // CD stage 2
        targetServer = serverMap.get('stage2')
        deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage2 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
        mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage2" )
        //runCitTests("CD-Stage2")

        // CD stage 3
        targetServer = serverMap.get('stage3')
        deployApp(userName, targetServer, port, deployBase, projectName, nexusRepo, nexusUser, nexusPass, "Stage3 testing", env.BUILD_ID, mergeToBranch, artifactName + ".mock")
        mockCdn( projectName, targetServer, userName, deployBase, "CD-Stage3" )
        //runJourneyTests("CD-Stage3")

        println( "Tagging artifacts");
        tagArtifact( env.BUILD_ID + "_mock", projectName, localArtifactPath, artifactName + ".mock", nexusRepo, nexusUser, nexusPass, tag, "Publish mocked artifact" )
        tagArtifact( env.BUILD_ID + "_live", projectName, localArtifactPath, artifactName + ".live", nexusRepo, nexusUser, nexusPass, tag, "Publish live artifact" )
        break;

    default:
      println "Git event ignored";
      currentBuild.displayName = "Git event ignored"
        def buildNumber = env.BUILD_ID
        node {
            sh "curl -X POST http://trigger:4615faafef698c25100208969391c9d6@jutiper.stack1.com:8080/job/execute-pipeline/${buildNumber}/doDelete"
        }
}




// ==================================================================================================
// Subs
// ==================================================================================================

private String getEventType ( payload ){

   if( payload.pull_request  && payload.action.toLowerCase().contains("opened") ){
      return "pull_req"
   }

   else if( payload.ref && payload.head_commit){
      if( payload.ref.split('/')[1].toLowerCase().contains('head') ){
         return "push"
      }

      else if( payload.ref.split('/')[1].toLowerCase().contains('tag') ){
         return "tag"
      }
   }
}

private void configureNpm(){
   sh "npm config set registry https://nexus.stack1.com:8081/nexus/content/repositories/public-npm-registry/"
   sh "npm config set strict-ssl false"
   sh "npm config set tmp /var/jenkins_home"
   sh "npm config set always-auth true"
   sh "npm config set _auth NDQwMDcwNDE6NTA1U3RhdGU="
}


private String getSemVer(){
   def jsonSlurper = new JsonSlurperClassic()
   def reader = new BufferedReader(new InputStreamReader(new FileInputStream("package.json"),"UTF-8"))
   data = jsonSlurper.parse(reader)
   println "DATA: " + data
   return data
}


public void mockCdn( String projectName, String targetServer, String userName, String deployBase, String cdStage ){
    node {
        echo "Stopping mock CDN http-server"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'pkill -f http-server' || true"

        echo "Creating mock CDN content"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName}/public/cdn && mkdir -p apinettransformation/react/cdn'"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName}/public/cdn && mv content apinettransformation/react/cdn'"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName}/public/cdn && mv *.json apinettransformation/react/cdn'"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName}/public && cp -r build cdn/apinettransformation/react/'"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName}/public && cp -r images cdn/apinettransformation/react'"

        echo "Starting mock CDN http-server"
        sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'http-server ${deployBase}/${projectName}/public > ${deployBase}/${projectName}/http-server.log 2>&1 &'"
   }
}


public void runFeatureTests(String cdStage, String reportHost){
   node {
        stage (cdStage +' \nFEATURE TESTING') {
            //sh 'python "selenium-tests/featureTest.py" > selenium-tests/featureTest.html'
            // echo "SELENIUM reports can be viewed at http://${reportHost}:8081/selenium-tests/featureTest.html"
            echo "Currently disabled - awaiting test creation for this stage"
        }
   }
}


public void runCitTests(cdStage){
   node {
        stage (cdStage +' \nCOMPONENT INTEGRATION TESTING') {
            echo "Currently disabled - awaiting test creation for this stage"
        }
   }
}


public void runJourneyTests(cdStage){
    node {
        stage (cdStage +' \nFULL JOURNEY TESTING') {
            echo "Currently disabled - awaiting test creation for this stage"
        }
    }
}


public void sonarTest( String projectKey, String branchName, String reportHost, String projectName, String sonarSource ) {
    node {
        stage ('Sonar static analysis') {
            withEnv(['SONAR_RUNNER_HOME=/opt/sonar-scanner/','SONAR_SCANNER_HOME=/opt/sonar-scanner/']) {
                sh "/opt/sonar-scanner/bin/sonar-scanner \
                    -D sonar.host.url=http://sonarqube:9000 \
                -D sonar.projectKey=${ projectKey } \
                -D sonar.projectName=${ projectKey } \
                    -D sonar.sources=${ sonarSource } \
                    -D sonar.exclusions=node_modules/**/* \
                    -D sonar.tests=test \
                    -D sonar.javascript.lcov.reportPath=coverage/lcov.info \
                    -D sonar.language=js"
            }
            println "SONAR reports can be viewed at http://${reportHost}:9000/dashboard/index/${projectKey}"
        }
    }
}

public void cloneRepo( String branch ){
   node {
      stage ('Clone Repository') {

         // We need to cache credentials so that our npm installs from Git work later on - this is a temp measure
         /*sh "rm -rf automation"
         withCredentials([usernameColonPassword(credentialsId: 'd161c80d-50f6-4307-9244-eeb23a96d294', variable: 'USERPASS')]) {
            sh "git clone https://$USERPASS@alm-github.systems.uk.foo/DTC-Enablers/automation.git"
         }
         sh "rm -rf automation"
            */
         // Now we've cloned a random repo to cache creds we can delete it and clone the repo we actually want :)
            checkout([$class: 'GitSCM',
                      branches: [[name: '*/' + branch]],
                      doGenerateSubmoduleConfigurations: false,
                      extensions: [],
                      submoduleCfg: [],
                      userRemoteConfigs: [[url: 'git@github.com:my_repo/my_project.git',
                                           credentialsId: 'my_creds',
                                           variable: 'USERPASS']]
            ])
        }
   }
}

public void npmInstall(){
   node {
       stage ('npm install') {
          configureNpm()
          sh "npm install"
       }
   }
}

public void npmUpdate(){
   node {
       stage ('npm update') {
            configureNpm()
          sh "npm update"
       }
   }
}

public void buildMock(){
   node {
           stage ('npm run buildMock') {
            sh "npm run buildMock"
       }
   }
}

public void buildMockProd(){
   node {
           stage ('Build mocked artifact') {
            sh "npm run buildMockProd"
       }
   }
}

public void build(){
   node {
           stage ('Build live artifact') {
            sh "npm run build"
       }
   }
}

public void unitTests(String reportHost, String projectName){
   node {
        stage ('Unit tests') {
            //sh "npm run react-unit"
            // println "UNIT test reports can be viewed at http://${reportHost}:8081/mochawesome-report/mochawesome.html"
            println "Unit test not present"
       }
   }
}

public void lcovTests(String reportHost, String projectName){
   node {
        stage ('Line coverage tests') {
            //sh "npm run test-hci"
            // println "Line coverage reports can be viewed at http://${reportHost}:8081/coverage/lcov-report/index.html"
            println "Line coverage not present"
       }
   }
}

public void uploadArtifact(String artifactPath, String artifactName, String nexusRepo, String nexusUser, String nexusPass, String sourcePath, String stageName, String buildCmd){
   node {
        stage ( stageName ) {

            if( buildCmd.toLowerCase().contains( "no-command" )){
                println("no build command specified")
            }else{
                println "Running build command: ${buildCmd}"
                sh buildCmd
            }

            println "Creating artifact and storing in temp location on build server at ${artifactPath}/${artifactName}"
            sh "mkdir -p ${artifactPath}"
            sh "tar -czf ${artifactPath}/${artifactName} ${sourcePath} --exclude='.git' --exclude='.idea' --exclude='.gitignore' --exclude='.scripts'"
            println "Uploading deployable artifact to Nexus repo at: $nexusRepo"
            sh "curl  --fail -u $nexusUser:$nexusPass -k --upload-file ${artifactPath}/${artifactName} $nexusRepo"
            println "Deleting temporary copy of artifact at ${artifactPath}"
            sh "rm -rf ${artifactPath}"
            println "Uploaded artifact ${artifactName} to ${nexusRepo}/${artifactName}"
        }
   }
}

public void tagArtifact( String buildId, String projectName, String artifactPath, String artifactName, String nexusRepo, String nexusUser, String nexusPass, String tag, String stageName ){
    node {
        stage (stageName) {
            println "Downloading ${nexusRepo}${artifactName} to /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName} for modification"
            sh "mkdir -p /var/jenkins_home/tmp/publish/${projectName}/${buildId} && \
            curl  --fail -u $nexusUser:$nexusPass -k -o /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName} ${nexusRepo}${artifactName}"


            println "Unpacking /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName} to /var/jenkins_home/tmp/publish/${projectName}/${buildId}"
            sh "tar xzf /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName} -C /var/jenkins_home/tmp/publish/${projectName}/${buildId}"

            println "Repacking /var/jenkins_home/tmp/publish/${projectName}/${buildId}/public to /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName}.${tag}.tar.gz"
            sh "tar -czf /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName}.${tag}.tar.gz -C /var/jenkins_home/tmp/publish/${projectName}/${buildId} public"

            println "Uploading tagged artifact to Nexus repo at: $nexusRepo"
            sh "curl  --fail -u $nexusUser:$nexusPass -k --upload-file  /var/jenkins_home/tmp/publish/${projectName}/${buildId}/${artifactName}.${tag}.tar.gz $nexusRepo"

            println "Deleting local copy of artifact at /var/jenkins_home/tmp/publish/${projectName}/${buildId}"
            sh "rm -rf /var/jenkins_home/tmp/publish/${projectName}/${buildId}"

            println("Artifact available at ${nexusRepo}/${artifactName}.${tag}.tar.gz ")
        }
    }
}

public void deployApp(String userName, String targetServer, String port, String deployBase, String projectName, String nexusRepo, String nexusUser, String nexusPass, String stageName, String buildId, String branchName, String artifactName){
   node {
       stage (stageName) {
         echo "Stopping previous version of ${projectName} application"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'pkill -f node\\ src/www' || true"

         echo "Remove previous version of ${projectName}"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'rm -rf ${deployBase}/${projectName}'"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'rm -rf ${deployBase}/tmp'"

         echo "Create dirs for new app"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase} && mkdir -p ${projectName}'"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase} && mkdir -p tmp/${projectName}/${buildId}'"

         echo "Downloading deployable artifact from Nexus repo at: $nexusRepo"
         sh "mkdir -p /var/jenkins_home/tmp/${projectName}/${buildId}"
         sh "curl  --fail -u $nexusUser:$nexusPass -k -o /var/jenkins_home/tmp/${projectName}/${buildId}/${projectName}.tar.gz ${nexusRepo}${artifactName}"

         echo "Copying deployable artifact to target server at ${deployBase}/tmp/${projectName}/${buildId}"
         sh "scp /var/jenkins_home/tmp/${projectName}/${buildId}/${projectName}.tar.gz ${userName}@${targetServer}:${deployBase}/tmp/${projectName}/${buildId}"

         echo "Clean up local artifact at /var/jenkins_home/tmp/${projectName}/${buildId}"
         sh "rm -rf /var/jenkins_home/tmp/${projectName}/${buildId}"

         echo "Unpacking deployable artifact to ${deployBase}/${projectName}"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'tar -xzf ${deployBase}/tmp/${projectName}/${buildId}/${projectName}.tar.gz -C ${deployBase}/${projectName}/ '"

         echo "Starting new version of ${projectName} application"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'chmod +x ${deployBase}/${projectName}/start.sh'"
         sh "ssh -q -o 'StrictHostKeyChecking=no' ${userName}@${targetServer} 'cd ${deployBase}/${projectName} && ./start.sh  dev dev ${branchName} ${buildId} 1234 true >>${deployBase}/${projectName}.log 2>&1 &'"

            echo "DONE. Build No: ${buildId}: http://${targetServer}:8080"
       }
   }
}
