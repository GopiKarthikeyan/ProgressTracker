package com.forge.hypertrophy.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.forge.hypertrophy.ui.screens.routine.DayEditorScreen
import com.forge.hypertrophy.ui.screens.routine.DayEditorViewModel
import com.forge.hypertrophy.ui.screens.routine.ExerciseEditorScreen
import com.forge.hypertrophy.ui.screens.routine.ExerciseEditorViewModel
import com.forge.hypertrophy.ui.screens.routine.ExerciseLibraryScreen
import com.forge.hypertrophy.ui.screens.routine.ExerciseLibraryViewModel
import com.forge.hypertrophy.ui.screens.routine.ProgramEditorScreen
import com.forge.hypertrophy.ui.screens.routine.ProgramEditorViewModel
import com.forge.hypertrophy.ui.screens.routine.ProgramListScreen
import com.forge.hypertrophy.ui.screens.routine.ProgramListViewModel
import com.forge.hypertrophy.ui.screens.routine.SkillEditorScreen
import com.forge.hypertrophy.ui.screens.routine.SkillEditorViewModel
import com.forge.hypertrophy.ui.screens.routine.SkillLibraryScreen
import com.forge.hypertrophy.ui.screens.routine.SkillLibraryViewModel
import com.forge.hypertrophy.ui.screens.routine.SlotEditorScreen
import com.forge.hypertrophy.ui.screens.routine.SlotEditorViewModel

fun NavGraphBuilder.routineGraph(navController: NavHostController) {
    navigation<RoutineRoute>(startDestination = ProgramListRoute) {
        composable<ProgramListRoute> {
            val viewModel: ProgramListViewModel = hiltViewModel()
            ProgramListScreen(
                viewModel = viewModel,
                onOpenProgram = { navController.navigate(ProgramEditorRoute(it)) },
                onOpenExercises = { navController.navigate(ExerciseLibraryRoute) },
                onOpenSkills = { navController.navigate(SkillLibraryRoute) },
                onImport = { uri -> navController.navigate(ImportPreviewRoute(uri = uri)) },
            )
        }
        composable<ProgramEditorRoute> {
            val viewModel: ProgramEditorViewModel = hiltViewModel()
            ProgramEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDay = { navController.navigate(DayEditorRoute(it)) },
            )
        }
        composable<DayEditorRoute> {
            val viewModel: DayEditorViewModel = hiltViewModel()
            DayEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSlot = { navController.navigate(SlotEditorRoute(it)) },
            )
        }
        composable<SlotEditorRoute> {
            val viewModel: SlotEditorViewModel = hiltViewModel()
            SlotEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseLibraryRoute> {
            val viewModel: ExerciseLibraryViewModel = hiltViewModel()
            ExerciseLibraryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenExercise = { navController.navigate(ExerciseEditorRoute(it)) },
                onAddExercise = { navController.navigate(ExerciseEditorRoute()) },
            )
        }
        composable<ExerciseEditorRoute> {
            val viewModel: ExerciseEditorViewModel = hiltViewModel()
            ExerciseEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<SkillLibraryRoute> {
            val viewModel: SkillLibraryViewModel = hiltViewModel()
            SkillLibraryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSkill = { navController.navigate(SkillEditorRoute(it)) },
                onAddSkill = { navController.navigate(SkillEditorRoute()) },
            )
        }
        composable<SkillEditorRoute> {
            val viewModel: SkillEditorViewModel = hiltViewModel()
            SkillEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
