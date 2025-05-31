package uk.scimone.diafit.journal.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.journal.presentation.JournalViewModel

val journalModule = module {
    viewModel { (userId: Int) ->
        JournalViewModel(get(), userId)
    }
}
