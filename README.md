# jenkins-pipeline-library

## module releaseContentBuilder

Le module releaseContentBuilder release la construction du contenu du mail envoye par Jenkins lorsque le pipeline atteint la phase {'Promote And Notify'.


Fragment de Jenkinsfile

```json
    stage('Promote And Notify'){
			when{
				expression { env.PIPELINE_STATUS == "RELEASED" }
			}
			steps{
			    script{
			    	def issues = readJSON file: 'jira-issues.json'
			    	releaseContent = releaseContentBuilder issues: issues, releaseVersion: "${env.RELEASE_VERSION}", env: env, build: currentBuild
			    }
			    
				mail     replyTo: '${DEFAULT_REPLYTO}', 
						 subject: "${env.PROJECT_TRG} : new release ${env.API_PL_VERSION} is ready to deploy.", 
						 to 	: "${env.RELEASE_MAIL_RECIPENTS}", 
						 mimeType: 'text/html',
						 body	: "${releaseContent}"
			}
		}
```

Le module prend en paramÃ¨tre:

* total: le nombre total de ticket embarquÃ© dans la version
* issues: la liste des tickets embarquÃ©s au format json
* env: les variables d'environnement disponbiles dans le contexte 
* build: le build Jenkins ayant executÃ© le pipeline

Le template de mail est contenu dans le fichier [cd.api.promote.template](resources/cd.api.promote.template) situÃ© dans le rÃ©pertoire ressource Ã  la racine du projet.

## module jiraCheckIssues.groovy

S'appuie sur les plugin JIRA plugin et JIRA Steps Plugin

Note: Desormais les variables ne sont plus passées par fichier mais propage dans le contexte du pipeline


@Library('jenkins-pipeline-library') _

    pipeline {
	 agent any
	 
    parameters {
        booleanParam name: 'SKIP_BUILD' 	  	  , defaultValue: false, description: 'Skip maven build stage ?'
        booleanParam name: 'SKIP_UNIT_TESTS'  	  , defaultValue: false, description: 'Skip unit tests ?'
        booleanParam name: 'SKIP_SONAR'		  	  , defaultValue: false, description: 'Skip SonarQube Analysis ?'
        booleanParam name: 'SKIP_DEPLOY'          , defaultValue: false, description: 'Skip deploy stage ?'
        booleanParam name: 'SKIP_ACCEPTANCE_TESTS', defaultValue: false, description: 'Skip acceptance tests ?'
        choice       name: 'ACCEPTANCE_TESTS_ENV' , description: 'Environment where acceptance tests should run', choices: 'dev1\ndev2\nap1\nap2\nap3'
        booleanParam name: 'DRY_RUN'              , defaultValue: false, description: 'Disable release step'
    }
	 
    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
        timeout(time: 2, unit: 'HOURS')
    }
    
    triggers {
        cron('H 10 * * 1-5')
    }
    
    stages {
    	stage('Environment Setup'){
			steps{
				script{
					echo "Loading Environment variables from file pipeline-conf.yml ..."
					pipelineConf = readYaml file: "pipeline-conf.yml"
					for(envVar in pipelineConf.envVars) {
						env.setProperty(envVar.key, envVar.value)
					}
					
					def pomModel = readMavenPom file: 'pom.xml'
					env.CURRENT_VERSION  = pomModel.version
					env.CURRENT_POM_ARTIFACT = pomModel.artifactId
					
					env.APPS_TO_DEPLOY = pipelineConf.envVars['APP_ARTIFACT_IDS'].collect{ '{\"a\":\"' + it + '\"}' }.join(', ')
					env.PIPELINE_STATUS = "SETUP"
					sh 'env'
				}
			}
		}
		
		stage('Release'){
			steps{
								script{
					def jiraIssues = jiraIssueSelector(issueSelector: [$class: 'JqlIssueSelector', jql: "project = SWADEV AND component = ${JIRA_COMPONENT_NAME} AND issuetype in (Defect, \"Functional Story\")  AND status in (Corrigé, \"a livrer\")"])
                    if(! jiraIssues?.empty) {
                        echo 'number of issues to deliver : ' + jiraIssues.size()   
                        // On genere la version de release et la prochaine version de dev
                        def mvnVersions = computeMavenVersions(version: "${env.CURRENT_VERSION}")
                        env.RELEASE_VERSION = mvnVersions.releaseVersion 
                        env.NEXT_DEV_VERSION = mvnVersions.nextDevelopmentVersion
                        
                        // On declenche la release
                        withMaven(maven: 'm-3.3', mavenSettingsConfig: 'user-maven-settings') {
						    //sh "mvn jgitflow:release-start -DreleaseVersion=${env.RELEASE_VERSION} -DdevelopmentVersion=${env.NEXT_DEV_VERSION}"
						    //sh 'mvn jgitflow:release-finish -Darguments="-Dmaven.test.skip=true"'
					    }
					    
					    // On crée la nouvelle version JIRA
                        def newVersion = jiraNewVersion(version: [ name: "${PROJECT_TRG} ${env.RELEASE_VERSION}",
                          											archived: false,
                          											released: false,
                          											description: "${JIRA_COMPONENT_NAME} version ${env.RELEASE_VERSION}",
                          											project: 'SWADEV' ])
                        
                        // On met a jour les tickets
                        jiraIssues.each { 
                            issue -> echo 'Updating issue : ' + issue
                            		 def existingIssue = jiraGetIssue idOrKey: issue
                            		 def fixVersions = existingIssue.data.fields.fixVersions << newVersion.data
                            		 def updatedIssue = [fields: [fixVersions: fixVersions]]
                                     response = jiraEditIssue idOrKey: issue, issue: updatedIssue
                                     
                                     //def transitionInput = [transition: [name: 'Livraison']] // ou Corrigé pour les annos
                                     //jiraTransitionIssue idOrKey: issue, input: transitionInput
                                     
                                     jiraAddComment comment: "{panel:bgColor=#97FF94}{code}Powered by Jenkins. Resolved in ${env.RELEASE_VERSION}.{code} {panel}", idOrKey: issue
                                     echo 'issue ' + issue + ' updated.'
                                     
                        }
                        
                        env.PIPELINE_STATUS = "RELEASED"
                    }
				}
			}
		}
    }
    }

